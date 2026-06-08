package com.voicetodo.llm

import android.util.Log
import com.google.litertlm.LiteRTLM as NativeLiteRTLM

/**
 * LiteRT-LM引擎包装类
 * 
 * 封装Google LiteRT-LM原生库，提供Kotlin友好的接口
 */
class LiteRTLMEngine {
    companion object {
        private const val TAG = "LiteRTLMEngine"
    }
    
    // 原生引擎实例
    private var nativeEngine: NativeLiteRTLM? = null
    
    // 是否已加载模型
    private var isModelLoaded = false
    
    /**
     * 加载模型
     * 
     * @param modelPath 模型文件路径
     * @return 是否加载成功
     */
    fun loadModel(modelPath: String): Boolean {
        try {
            Log.d(TAG, "加载模型: $modelPath")
            
            // 创建原生引擎实例
            val engine = NativeLiteRTLM()
            
            // 加载模型
            val success = engine.loadModel(modelPath)
            
            if (success) {
                nativeEngine = engine
                isModelLoaded = true
                Log.d(TAG, "模型加载成功")
            } else {
                Log.e(TAG, "模型加载失败")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "加载模型时发生异常", e)
            return false
        }
    }
    
    /**
     * 生成文本
     * 
     * @param prompt 输入提示词
     * @param config 推理配置
     * @param onToken Token生成回调
     */
    fun generate(
        prompt: String,
        config: InferenceConfig,
        onToken: (String) -> Unit
    ) {
        try {
            Log.d(TAG, "开始生成文本")
            
            val engine = nativeEngine
            if (engine == null || !isModelLoaded) {
                Log.e(TAG, "引擎未初始化或模型未加载")
                return
            }
            
            // 设置推理参数
            engine.setMaxTokens(config.maxTokens)
            engine.setTemperature(config.temperature)
            engine.setTopP(config.topP)
            engine.setTopK(config.topK)
            engine.setRepeatPenalty(config.repeatPenalty)
            
            // 设置Token回调
            engine.setTokenCallback { token ->
                onToken(token)
            }
            
            // 执行生成
            engine.generate(prompt)
            
            Log.d(TAG, "文本生成完成")
        } catch (e: Exception) {
            Log.e(TAG, "生成文本时发生异常", e)
        }
    }
    
    /**
     * 停止生成
     */
    fun stopGeneration() {
        try {
            nativeEngine?.stopGeneration()
            Log.d(TAG, "已停止生成")
        } catch (e: Exception) {
            Log.e(TAG, "停止生成时发生异常", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            nativeEngine?.release()
            nativeEngine = null
            isModelLoaded = false
            Log.d(TAG, "资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时发生异常", e)
        }
    }
}
