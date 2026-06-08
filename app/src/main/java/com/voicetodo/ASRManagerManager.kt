package com.voicetodo

import android.content.Context
import com.voicetodo.asr.ASRManager

/**
 * ASRManager单例管理类
 * 确保ASRManager在应用生命周期内只创建一次
 */
object ASRManagerManager {
    
    private var asrManager: ASRManager? = null
    
    /**
     * 获取ASRManager单例
     */
    fun getASRManager(context: Context): ASRManager {
        if (asrManager == null) {
            asrManager = ASRManager(context)
        }
        return asrManager!!
    }
    
    /**
     * 释放ASRManager资源
     */
    fun release() {
        asrManager?.release()
        asrManager = null
    }
}
