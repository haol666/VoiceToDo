package com.voicetodo.asr

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.voicetodo.data.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 语音识别管理器
 * 
 * 功能：
 * - 集成Sherpa-ONNX库进行流式语音识别
 * - 使用AudioRecord采集16kHz/单声道/PCM16音频
 * - 实现实音频流识别和实时文本回调
 * - 解决AssetManager陷阱（模型位于filesDir时AssetManager置为null）
 */
class ASRManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    
    companion object {
        private const val TAG = "ASRManager"
        
        // 音频配置
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNELS_PER_FRAME = 1
        private const val BITS_PER_SAMPLE = 16
        
        // 音频缓冲区大小
        private const val BUFFER_SIZE_IN_MS = 100 // 100ms
        private const val BUFFER_SIZE_IN_BYTES = 
            SAMPLE_RATE * CHANNELS_PER_FRAME * BITS_PER_SAMPLE / 8 * BUFFER_SIZE_IN_MS / 1000
        
        // 识别状态
        private const val RECOGNITION_INTERVAL_MS = 100 // 识别间隔
    }
    
    // ========== 识别状态 ==========
    
    enum class RecognitionState {
        IDLE,           // 空闲
        INITIALIZING,   // 初始化中
        LISTENING,      // 监听中
        PROCESSING,     // 处理中
        ERROR           // 错误
    }
    
    private val _state = MutableStateFlow(RecognitionState.IDLE)
    val state: StateFlow<RecognitionState> = _state
    
    private val _recognizedText = MutableStateFlow("")
"    val recognizedText: StateFlow<String> = _recognizedText
    
    private val _isFinal = MutableStateFlow(false)
    val isFinal: StateFlow<Boolean> = _isFinal
    
    // ========== 音频录制 ==========
    
    private var audioRecord: AudioRecord? = null
    private var audioBufferSize: Int = 0
    private var isRecording = false
    
    // ========== Sherpa-ONNX 识别器 ==========
    
    private var onlineRecognizer: OnlineRecognizer? = null
    private var onlineStream: OnlineStream? = null
    
    // ========== 协程 ==========
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null
    
    // ========== 回调接口 ==========
    
    /**
     * 识别结果回调接口
     */
    interface RecognitionCallback {
        /**
         * 实时识别结果
         * @param text 当前识别的文本（可能包含部分结果）
         * @param isFinal 是否为最终结果
         */
        fun onResult(text: String, isFinal: Boolean)
        
        /**
         * 识别错误
         * @param error 错误信息
         */
        fun onError(error: String)
        
        /**
         * 识别开始
         */
        fun onStart()
        
        /**
         * 识别结束
         */
        fun onEnd()
    }
    
    private var callback: RecognitionCallback? = null
    
    // ========== 初始化 ==========
    
    /**
     * 初始化ASR识别器
     * @param modelPath 模型文件路径（可以是assets路径或filesDir路径）
     * @return 是否初始化成功
     */
    fun initialize(modelPath: String): Boolean {
        Log.d(TAG, "初始化ASR识别器，模型路径: $modelPath")
        
        try {
            _state.value = RecognitionState.INITIALIZING
            
            // 检查模型文件是否存在
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "模型文件不存在: $modelPath")
                _state.value = RecognitionState.ERROR
                callback?.onError("模型文件不存在")
                return false
            }
            
            // 初始化音频录制
            if (!initAudioRecord()) {
udi                Log.e(TAG, "音频录制初始化失败")
                _state.value = RecognitionState.ERROR
                callback?.onError("音频录制初始化失败")
                return false
            }
            
            // 初始化Sherpa-ONNX识别器
            // ⚠️ 关键：解决AssetManager陷阱
            // 如果模型位于filesDir，则AssetManager参数必须为null
            val assetManager = if (modelPath.startsWith(context.filesDir.absolutePath)) {
                null
            } else {
                context.assets
            }
            
            onlineRecognizer = OnlineRecognizer(
                assetManager = assetManager,
                modelConfig = OnlineRecognizerConfig(
                    tokens = "$modelPath/tokens.txt",
                    encoder = "$modelPath/encoder.onnx",
                    decoder = "$modelPath/decoder.onnx",
                    joiner = "$modelPath/joiner.onnx",
                    numThreads = 1,
                    sampleRate = SAMPLE_RATE,
                    featureConfig = FeatureConfig(
                        sampleRate = SAMPLE_RATE,
                        featureDim = 80
                    ),
                    decodingMethod = "greedy_search",
                    maxActiveStates = 40
                )
            )
            
            Log.d(TAG, "ASR识别器初始化成功")
            _state.value = RecognitionState.IDLE
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "ASR识别器初始化失败", e)
            _state.value = RecognitionState.ERROR
            callback?.onError("初始化失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 初始化音频录制
     */
    private fun initAudioRecord(): Boolean {
        try {
            // 计算缓冲区大小
            audioBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (audioBufferSize == AudioRecord.ERROR || audioBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "无法获取音频缓冲区大小")
                return false
            }
            
            // 确保缓冲区足够大
            if (audioBufferSize < BUFFER_SIZE_IN_BYTES) {
                audioBufferSize = BUFFER_SIZE_IN_BYTES
            }
            
            // 创建AudioRecord
            audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(audioBufferSize)
                    .build()
            } else {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    audioBufferSize
                )
            }
            
            Log.d(TAG, "音频录制初始化成功，缓冲区大小: $audioBufferSize")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "音频录制初始化失败", e)
            return false
        }
        catch (e: SecurityException) {
            Log.e(TAG, "没有录音权限", e)
            return false
        }
    }
    
    // ========== 开始识别 ==========
    
    /**
     * 开始语音识别
     * @param callback 识别结果回调
     */
    fun startRecognition(callback: RecognitionCallback) {
        Log.d(TAG, "开始语音识别")
        
        if (_state.value != RecognitionState.IDLE) {
            Log.w(TAG, "识别器不在空闲状态，当前状态: ${_state.value}")
            return
        }
        
        if (onlineRecognizer == null) {
            Log.e(TAG, "识别器未初始化")
            this.callback?.onError("识别器未初始化")
            return
        }
        
        this.callback = callback
        
        try {
            // 创建识别流
            onlineStream = onlineRecognizer!!.createStream()
            
            // 开始录音
            audioRecord?.startRecording()
            isRecording = true
            
            _state.value = RecognitionState.LISTENING
            callback.onStart()
            
            // 启动协程进行音频采集和识别
            recordingJob = scope.launch {
                processAudioStream()
            }
            
            Log.d(TAG, "语音识别已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败", e)
            _state.value = RecognitionState.ERROR
            callback?.onError("启动失败: ${e.message}")
        }
    }
    
    /**
     * 处理音频流
     */
    private suspend fun processAudioStream() {
        Log.d(TAG, "开始处理音频流")
        
        val buffer = ByteArray(audioBufferSize)
        
        while (isRecording && isActive) {
            try {
                // 读取音频数据
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // 归一化音频数据并送入识别器
                    processAudioData(buffer, bytesRead)
            
                    // 定期进行识别
                    recognize()
                }
                
                // 短暂休眠，避免过度占用CPU
                delay(RECOGNITION_INTERVAL_MS.toLong())
                
            } catch (e: Exception) {
                Log.e(TAG, "处理音频数据失败", e)
                break
            }
        }
        
        Log.d(TAG, "音频流处理结束")
    }
    
    /**
     * 处理音频数据（归一化）
     * @param buffer 音频数据缓冲区
     * @param bytesRead 读取的字节数
     */
    private fun processAudioData(buffer: ByteArray, bytesRead: Int) {
        // 将PCM16数据转换为浮点数组并归一化到[-1, 1]范围
        val samples = FloatArray(bytesRead / 2)
        
        for (i in samples.indices) {
            val index = i * 2
            // PCM16是16位有符号整数，范围是[-32768, 32767]
            val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)).toShort()
            // 归一化到[-1, 1]
            samples[i] = sample / 32768.0f
        }
        
        // 将归一化后的音频数据送入识别流
        onlineStream?.acceptWaveform(SAMPLE_RATE, samples)
    }
    
    /**
     * 执行识别
     */
    private fun recognize() {
        try {
            // 判断是否有语音活动
            if (onlineStream?.isEndpoint() == true) {
                // 检测到语音结束，进行最终识别
                onlineRecognizer!!.decode(onlineStream!!)
                
                val result = onlineStream!!.result
                if (result.text.isNotEmpty()) {
                    val finalText = result.text
                    
                    // 更新状态
                    _recognizedText.value = finalText
                    _isFinal.value = true
                    
                    // 回调
                    callback?.onResult(finalText, true)
                    
                    Log.d(TAG, "最终识别结果: $finalText")
                }
                
                // 重置识别流
                onlineStream?.reset()
                _isFinal.value = false
                
            } else {
                // 进行中间识别
                onlineRecognizer!!.decode(onlineStream!!)
                
                val result = onlineStream!!.result
                if (result.text.isNotEmpty()) {
                    val partialText = result.text
                    
                    // 更新状态
                    _recognizedText.value = partialText
                    
                    // 回调
                    callback?.onResult(partialText, false)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "识别失败", e)
        }
    }
    
    // ========== 停止识别 ==========
    
    /**
     * 停止语音识别
     */
    fun stopRecognition() {
        Log.d(TAG, "停止语音识别")
        
        if (_state.value == RecognitionState.IDLE) {
            return
        }
        
        try {
            isRecording = false
            
            // 停止录音
            audioRecord?.stop()
            
            // 取消协程
            recordingJob?.cancel()
            recordingJob = null
            
            // 进行最终识别
            if (onlineStream != null && onlineRecognizer != null) {
                onlineRecognizer!!.decode(onlineStream!!)
                
                val result = onlineStream!!.result
                if (result.text.isNotEmpty()) {
                    val finalText = result.text
                    
                    _recognizedText.value = finalText
                    _isFinal.value = true
                    
                    callback?.onResult(finalText, true)
                    
                    Log.d(TAG, "最终识别结果: $finalText")
                }
            }
            
            // 释放识别流
            onlineStream?.release()
            onlineStream = null
            
            _state.value = RecognitionState.IDLE
            callback?.onEnd()
            
            Log.d(TAG, "语音识别已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止语音识别失败", e)
            _state.value = RecognitionState.ERROR
        }
    }
    
    // ========== 资源释放 ==========
    
    /**
     * 释放所有资源
     */
    fun release() {
        Log.d(TAG, "释放ASR资源")
        
        // 停止识别
        stopRecognition()
        
        // 释放识别器
        onlineRecognizer?.release()
        onlineRecognizer = null
        
        // 释放音频录制
        audioRecord?.release()
        audioRecord = null
        
        // 取消协程
        scope.cancel()
        
        _state.value = RecognitionState.IDLE
        
        Log.d(TAG, "ASR资源已释放")
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 是否正在识别
     */
    fun isRecognizing(): Boolean {
        return _state.value == RecognitionState.LISTENING || _state.value == RecognitionState.PROCESSING
    }
    
    /**
     * 获取当前识别的文本
     */
    fun getCurrentText(): String {
        return _recognizedText.value
    }
    
    /**
     * 清除当前识别的文本
     */
    fun clearText() {
        _recognizedText.value = ""
        _isFinal.value = false
    }
}
