package com.voicetodo.asr

/**
 * 在线识别结果类
 */
data class OnlineRecognizerResult(
    /**
     * 识别的文本
     */
    val text: String = "",
    
    /**
     * Token ID列表
     */
    val tokens: List<Int> = emptyList(),
    
    /**
     * 时间戳列表（秒）
     */
    val timestamps: List<Float> = emptyList(),
    
    /**
     * 是否为最终结果
     */
    val isFinal: Boolean = false
)
