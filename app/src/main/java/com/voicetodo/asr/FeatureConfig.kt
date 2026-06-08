package com.voicetodo.asr

/**
 * 特征配置类
 * 用于配置MFCC特征提取参数
 */
data class FeatureConfig(
    /**
     * 采样率（Hz）
     */
    val sampleRate: Int = 16000,
    
    /**
     * 特征维度
     */
    val featureDim: Int = 80,
    
    /**
     * 帧移（毫秒）
     */
    val frameShiftMs: Int = 10,
    
    /**
     * 帧长（毫秒）
     */
    val frameLengthMs: Int = 25,
    
    /**
     * 预加重系数
     */
    val preemphCoeff: Float = 0.97f,
    
    /**
     * 倒谱提升
     */
    val cepstralLifter: Float = 22.0f,
    
    /**
     * 使用能量特征
     */
    val useEnergy: Boolean = false
)
