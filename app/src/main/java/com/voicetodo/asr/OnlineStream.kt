package com.voicetodo.asr

import android.util.Log

/**
 * 在线识别流类
 * 代表一个连续的语音识别流
 */
class OnlineStream(
    private val recognizer: OnlineRecognizer,
    val nativePtr: Long
) {
    
    companion object {
        private const val TAG = "OnlineStream"
        
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
    
    private var isReleased = false
    
    /**
     * 接收音频波形数据
     * @param sampleRate 采样率
     * @param samples 归一化的音频样本数组（范围[-1, 1]）
     */
    fun acceptWaveform(sampleRate: Int, samples: FloatArray) {
        if (isReleased) {
            Log.w(TAG, "OnlineStream已释放，无法接收音频数据")
            return
        }
        
        nativeAcceptWaveform(nativePtr, sampleRate, samples)
    }
    
    /**
     * 输入结束，等待所有音频处理完成
     */
    fun inputFinished() {
        if (isReleased) {
            Log.w(TAG, "OnlineStream已释放")
            return
        }
        
        nativeInputFinished(nativePtr)
    }
    
    /**
     * 获取识别结果
     * @return 识别结果
     */
    fun getResult(): OnlineRecognizerResult {
        if (isReleased) {
            Log.w(TAG, "OnlineStream已释放")
            return OnlineRecognizerResult()
        }
        
        return nativeGetResult(nativePtr)
    }
    
    /**
     * 重置识别流
     * 清除所有已识别的内容，准备开始新的识别
     */
    fun reset() {
        if (isReleased) {
            Log.w(TAG, "OnlineStream已释放")
            return
        }
        
        nativeReset(nativePtr)
    }
    
    /**
     * 判断是否到达语音端点
     * @return 是否到达端点
     */
    fun isEndpoint(): Boolean {
        if (isReleased) {
            Log.w(TAG, "OnlineStream已释放")
            return false
        }
        
        return nativeIsEndpoint(nativePtr)
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
    
    // ========== Native方法 ==========
    
    private external fun nativeAcceptWaveform(ptr: Long, sampleRate: Int, samples: FloatArray)
    private external fun nativeInputFinished(ptr: Long)
    private external fun nativeGetResult(ptr: Long): OnlineRecognizerResult
    private external fun nativeReset(ptr: Long)
    private external fun nativeIsEndpoint(ptr: Long): Boolean
    private external fun nativeRelease(ptr: Long)
}
