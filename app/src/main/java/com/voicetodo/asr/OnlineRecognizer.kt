package com.voicetodo.asr

import android.content.res.AssetManager
import android.util.Log

/**
 * 在线识别器类
 * 基于Sherpa-ONNX的流式语音识别器
 */
class OnlineRecognizer(
    assetManager: AssetManager?,
    config: OnlineRecognizerConfig
) {
    
    companion object {
        private const val TAG = "OnlineRecognizer"
        
        // 加载Sherpa-ONNX native库
        init {
            try {
                System.loadLibrary("sherpa-onnx-jni")
                Log.d(TAG, "Sherpa-ONNX JNI库加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Sherpa-ONNX JNI库加载失败", e)
                throw e
            }
        }
    }
    
    private val nativePtr: Long
    private var isReleased = false
    
    init {
        // ⚠️ 关键：解决AssetManager陷阱
        // 如果模型位于filesDir，则AssetManager参数必须为null
        val configJson = configToJson(config)
        
        Log.d(TAG, "初始化OnlineRecognizer")
        Log.d(TAG, "配置: $configJson")
        
        nativePtr = if (assetManager != null) {
            nativeCreateWithAssetManager(assetManager, configJson)
        } else {
            nativeCreateWithoutAssetManager(configJson)
        }
        
        if (nativePtr == 0L) {
            Log.e(TAG, "OnlineRecognizer初始化失败")
            throw RuntimeException("OnlineRecognizer初始化失败")
        }
        
        Log.d(TAG, "OnlineRecognizer初始化成功")
    }
    
    /**
     * 创建识别流
     * @return 新的识别流
     */
    fun createStream(): OnlineStream {
        if (isReleased) {
            Log.e(TAG, "OnlineRecognizer已释放")
            throw IllegalStateException("OnlineRecognizer已释放")
        }
        
        val streamPtr = nativeCreateStream(nativePtr)
        return OnlineStream(this, streamPtr)
    }
    
    /**
     * 解码识别流
     * @param stream 识别流
     */
    fun decode(stream: OnlineStream) {
        if (isReleased) {
            Log.w(TAG, "OnlineRecognizer已释放")
            return
        }
        
        nativeDecode(nativePtr, stream.nativePtr)
    }
    
    /**
     * 释放native资源
     */
    fun release() {
        if (isReleased) {
            return
        }
        
        nativeRelease(nativePtr)
        isReleased = true
    }
    
    /**
     * 将配置转换为JSON字符串
     */
    private fun configToJson(config: OnlineRecognizerConfig): String {
        return """{
  "feat_config": {
    "sample_rate": ${config.featureConfig.sampleRate},
    "feature_dim": ${config.featureConfig.featureDim},
    "frame_shift_ms": ${config.featureConfig.frameShiftMs},
    "frame_length_ms": ${config.featureConfig.frameLengthMs},
    "preemph_coeff": ${config.featureConfig.preemphCoeff},
    "cepstral_lifter": ${config.featureConfig.cepstralLifter},
    "use_energy": ${config.featureConfig.useEnergy}
  },
  "model_config": {
    "tokens": "${config.tokens}",
    "encoder": "${config.encoder}",
    "decoder": "${config.decoder}",
    "joiner": "${config.joiner}",
    "num_threads": ${config.numThreads},
    "sample_rate": ${config.sampleRate},
    "decoding_method": "${config.decodingMethod}",
    "max_active_states": ${config.maxActiveStates},
    "beam_size": ${config.beamSize},
    "lm_scale": ${config.lmScale},
    "word_insertion_penalty": ${config.wordInsertionPenalty},
    "blank_penalty": ${config.blankPenalty}
  }
}"""
    }
    
    // ========== Native方法 ==========
    
    private external fun nativeCreateWithAssetManager(
        assetManager: AssetManager,
        configJson: String
    ): Long
    
    private external fun nativeCreateWithoutAssetManager(
        configJson: String
    ): Long
    
    private external fun nativeCreateStream(ptr: Long): Long
    private external fun nativeDecode(ptr: Long, streamPtr: Long)
    private external fun nativeRelease(ptr: Long)
}
