package com.voicetodo.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import com.voicetodo.data.PreferencesManager

/**
 * 大语言模型推理管理器
 * 
 * 负责管理LiteRT-LM模型的加载、推理和资源释放
 * 
 * @property context Android上下文
 * @property preferencesManager 配置管理器
 */
class LLMManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "LLMManager"
        
        // 默认推理参数
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_P = 0.9f
    }
    
    // LiteRT-LM引擎实例
    private var engine: LiteRTLMEngine? = null
    
    // 当前加载的模型路径
    private var currentModelPath: String? = null
    
    // 推理状态
    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 初始化LLM引擎
     * 
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "初始化LLM引擎...")
            
            // 检查模型是否已配置
            val modelPath = preferencesManager.getLLMModelPath()
            if (modelPath.isNullOrEmpty()) {
                Log.w(TAG, "LLM模型路径未配置")
                _inferenceState.value = InferenceState.Error("模型路径未配置")
                return@withContext false
            }
            
            // 检查模型文件是否存在
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "模型文件不存在: $modelPath")
                _inferenceState.value = InferenceState.Error("模型文件不存在")
                return@withContext false
            }
            
            // 加载模型
            loadModel(modelPath)
            
            Log.d(TAG, "LLM引擎初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化LLM引擎失败", e)
            _inferenceState.value = InferenceState.Error("初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 加载模型
     * 
     * @param modelPath 模型文件路径
     * @return 加载是否成功
     */
    private suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "加载模型: $modelPath")
            
            // 如果已有引擎且模型路径相同，则无需重新加载
            if (engine != null && currentModelPath == modelPath) {
                Log.d(TAG, "模型已加载，无需重新加载")
                return@withContext true
            }
            
            // 释放旧引擎
            releaseEngine()
            
            // 创建新引擎
            val newEngine = LiteRTLMEngine()
            val success = newEngine.loadModel(modelPath)
            
            if (success) {
                engine = newEngine
                currentModelPath = modelPath
                Log.d(TAG, "模型加载成功")
                true
            } else {
                Log.e(TAG, "模型加载失败")
                _inferenceState.value = InferenceState.Error("模型加载失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载模型时发生异常", e)
            _inferenceState.value = InferenceState.Error("加载模型异常: ${e.message}")
            false
        }
    }
    
    /**
     * 切换模型
     * 
     * @param modelPath 新模型路径
     * @return 切换是否成功
     */
    suspend fun switchModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "切换模型到: $modelPath")
            
            // 检查模型文件是否存在
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "模型文件不存在: $modelPath")
                _inferenceState.value = InferenceState.Error("模型文件不存在")
                return@withContext false
            }
            
            // 加载新模型
            val success = loadModel(modelPath)
            
            if (success) {
                // 更新配置
                preferencesManager.setLLMModelPath(modelPath)
                Log.d(TAG, "模型切换成功")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "切换模型时发生异常", e)
            _inferenceState.value = InferenceState.Error("切换模型异常: ${e.message}")
            false
        }
    }
    
    /**
     * 异步发送消息进行推理
     * 
     * @param prompt 输入提示词
     * @param onProgress 推理进度回调
     * @return 推理结果
     */
    suspend fun sendMessageAsync(
        prompt: String,
        onProgress: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始推理，prompt长度: ${prompt.length}")
            
            // 检查引擎是否已初始化
            val currentEngine = engine
            if (currentEngine == null) {
                Log.e(TAG, "引擎未初始化")
                _inferenceState.value = InferenceState.Error("引擎未初始化")
                return@withContext ""
            }
            
            _inferenceState.value = InferenceState.Generating
            
            // 获取推理参数
            val maxTokens = preferencesManager.getMaxTokens()
            val temperature = preferencesManager.getTemperature()
            val topP = preferencesManager.getTopP()
            
            // 构建推理配置
            val config = InferenceConfig(
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP
            )
            
            // 执行推理
            val result = StringBuilder()
            currentEngine.generate(
                prompt = prompt,
                config = config,
                onToken = { token ->
                    result.append(token)
                    onProgress?.invoke(result.toString())
                }
            )
            
            val finalResult = result.toString()
            Log.d(TAG, "推理完成，结果长度: ${finalResult.length}")
            
            _inferenceState.value = InferenceState.Idle
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "推理时发生异常", e)
            _inferenceState.value = InferenceState.Error("推理异常: ${e.message}")
            ""
        }
    }
    
    /**
     * 同步发送消息进行推理（阻塞调用）
     * 
     * @param prompt 输入提示词
     * @return 推理结果
     */
    fun sendMessage(prompt: String): String {
        return runBlocking {
            sendMessageAsync(prompt)
        }
    }
    
    /**
     * 检查引擎是否已就绪加载
     * 
     * @return 是否已就绪
     */
    fun isReady(): Boolean {
        return engine != null && currentModelPath != null
    }
    
    /**
     * 获取当前模型路径
     * 
     * @return 模型路径，未加载则返回null
     */
    fun getCurrentModelPath(): String? {
        return currentModelPath
    }
    
    /**
     * 释放引擎资源
     */
    private fun releaseEngine() {
        try {
            engine?.release()
            engine = null
            currentModelPath = null
            Log.d(TAG, "引擎资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放引擎资源时发生异常", e)
        }
    }
    
    /**
     * 销毁LLM管理器，释放所有资源
     */
    fun destroy() {
        Log.d(TAG, "销毁LLM管理器")
        releaseEngine()
        scope.cancel()
    }
}

/**
 * 推理状态
 */
sealed class InferenceState {
    /** 空闲状态 */
    data object Idle : InferenceState()
    
    /** 正在生成 */
    data object Generating : InferenceState()
    
    /** 错误状态 */
    data class Error(val message: String) : InferenceState()
}

/**
 * 推理配置
 */
data class InferenceConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.0f
)
