package com.voicetodo.asr

/**
 * 在线识别器配置类
 * 用于配置Sherpa-ONNX在线识别器
 */
data class OnlineRecognizerConfig(
    /**
     * Token文件路径
     */
    val tokens: String,
    
    /**
     * Encoder模型路径
     */
    val encoder: String,
    
    /**
     * Decoder模型路径
     */
    val decoder: String,
    
    /**
     * Joiner模型路径
     */
    val joiner: String,
    
    /**
     * 线程数
     */
    val numThreads: Int = 1,
    
    /**
     * 采样率
     */
    val sampleRate: Int = 16000,
    
    /**
     * 特征配置
     */
    val featureConfig: FeatureConfig = FeatureConfig(),
    
    /**
     * 解码方法：greedy_search 或 modified_beam_search
     */
    val decodingMethod: String = "greedy_search",
    
    /**
     * 最大激活状态数（用于beam search）
     */
    val maxActiveStates: Int = 40,
    
    /**
     * Beam大小（用于beam search）
     */
    val beamSize: Int = 10,
    
    /**
     * 语言模型权重
     */
    val lmScale: Float = 1.0f,
    
    /**
     * 词插入惩罚
     */
    val wordInsertionPenalty: Float = 0.0f,
    
    /**
     * 空白惩罚
     */
    val blankPenalty: Float = 0.0f
)
