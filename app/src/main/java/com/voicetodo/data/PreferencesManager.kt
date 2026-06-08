package com.voicetodo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用配置管理类
 * 基于SharedPreferences存储用户设置和配置
 */
class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "voicetodo_prefs"
        
        // 模型路径相关
        private const val KEY_ASR_MODEL_PATH = "asr_model_path"
        private const val KEY_LLM_MODEL_PATH = "llm_model_path"
        
        // LLM推理参数
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        
        // 语音识别参数
        private const val KEY_SAMPLE_RATE = "sample_rate"
        private const val KEY_LANGUAGE = "language"
        
        // 其他设置
        private const val KEY_AUTO_SUMMARY = "auto_summary"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_DARK_MODE = "dark_mode"
        
        // 默认值
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val DEFAULT_LANGUAGE = "zh-CN"
        private const val DEFAULT_AUTO_SUMMARY = true
        private const val DEFAULT_NOTIFICATION_ENABLED = true
        private const val DEFAULT_DARK_MODE = false
    }
    
    // ========== 模型路径设置 ==========
    
    /**
     * 设置ASR模型路径
     */
    fun setAsrModelPath(path: String) {
        prefs.edit().putString(KEY_ASR_MODEL_PATH, path).apply()
    }
    
    /**
     * 获取ASR模型路径
     */
    fun getAsrModelPath(): String? {
        return prefs.getString(KEY_ASR_MODEL_PATH, null)
    }
    
    /**
     * 设置LLM模型路径
     */
    fun setLlmModelPath(path: String) {
        prefs.edit().putString(KEY_LLM_MODEL_PATH, path).apply()
    }
    
    /**
     * 获取LLM模型路径
     */
    fun getLlmModelPath(): String? {
        return prefs.getString(KEY_LLM_MODEL_PATH, null)
    }
    
    // ========== LLM推理参数 ==========
    
    /**
     * 设置最大Token数
     */
    fun setMaxTokens(maxTokens: Int) {
        prefs.edit().putInt(KEY_MAX_TOKENS, maxTokens).apply()
    }
    
    /**
     * 获取最大Token数
     */
    fun getMaxTokens(): Int {
        return prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
    }
    
    /**
     * 设置Temperature参数
     */
    fun setTemperature(temperature: Float) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temperature).apply()
    }
    
    /**
     * 获取Temperature参数
     */
    fun getTemperature(): Float {
        return prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
    }
    
    /**
     * 设置Top-P参数
     */
    fun setTopP(topP: Float) {
        prefs.edit().putFloat(KEY_TOP_P, topP).apply()
    }
    
    /**
     * 获取Top-P参数
     */
    fun getTopP(): Float {
        return prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P)
    }
    
    // ========== 语音识别参数 ==========
    
    /**
     * 设置采样率
     */
    fun setSampleRate(sampleRate: Int) {
        prefs.edit().putInt(KEY_SAMPLE_RATE, sampleRate).apply()
    }
    
    /**
     * 获取采样率
     */
    fun getSampleRate(): Int {
        return prefs.getInt(KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
    }
    
    /**
     * 设置语言
     */
    fun setLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }
    
    /**
     * 获取语言
     */
    fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    // ========== 其他设置 ==========
    
    /**
     * 设置是否自动生成摘要
     */
    fun setAutoSummary(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SUMMARY, enabled).apply()
    }
    
    /**
     * 获取是否自动生成摘要
     */
    fun isAutoSummaryEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SUMMARY, DEFAULT_AUTO_SUMMARY)
    }
    
    /**
     * 设置是否启用通知
     */
    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()
    }
    
    /**
     * 获取是否启用通知
     */
    fun isNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_ENABLED, DEFAULT_NOTIFICATION_ENABLED)
    }
    
    /**
     * 设置深色模式
     */
    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
    
    /**
     * 获取深色模式
     */
    fun isDarkModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE)
    }
    
    // ========== 通用方法 ==========
    
    /**
     * 清除所有设置
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    /**
     * 检查模型路径是否已配置
     */
    fun isModelConfigured(): Boolean {
        return getAsrModelPath() != null && getLlmModelPath() != null
    }
}
