package com.voicetodo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voicetodo.MainActivity
import com.voicetodo.asr.ASRManager
import com.voicetodo.data.PreferencesManager
import kotlinx.coroutines.*

/**
 * 语音识别服务
 * 
 * 功能：
 * - 作为前台服务运行，确保录音不被系统杀死
 * - 集成ASRManager进行流式语音识别
 * - 提供服务接口供Activity调用
 * - 显示持续的通知
 */
class VoiceRecognitionService : Service() {
    
    companion object {
        private const val TAG = "VoiceRecognitionService"
        
        // 服务ID
        const val SERVICE_ID = 1001
        
        // 通知渠道
        private const val CHANNEL_ID = "voice_recognition_channel"
        private const val CHANNEL_NAME = "语音识别服务"
        private const val CHANNEL_DESCRIPTION = "语音识别服务通知"
        
        // 广播Action
        const val ACTION_START_RECOGNITION = "com.voicetodo.START_RECOGNITION"
        const val ACTION_STOP_RECOGNITION = "com.voicetodo.STOP_RECOGNITION"
        const val ACTION_RECOGNITION_RESULT = "com.voicetodo.RECOGNITION_RESULT"
        const val ACTION_RECOGNITION_ERROR = "com.voicetodo.RECOGNITION_ERROR"
        
        // 广播Extra
        const val EXTRA_TEXT = "text"
        const val EXTRA_IS_FINAL = "is_final"
        const val EXTRA_ERROR = "error"
        
        // 通知Action
        const val ACTION_NOTIFICATION_STOP = "com.voicetodo.NOTIFICATION_STOP"
        
        /**
         * 启动语音识别服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, VoiceRecognitionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 开始识别
         */
        fun startRecognition(context: Context) {
            val intent = Intent(context, VoiceRecognitionService::class.java).apply {
                action = ACTION_START_RECOGNITION
            }
            context.startService(intent)
        }
        
        /**
         * 停止识别
         */
        fun stopRecognition(context: Context) {
            val intent = Intent(context, VoiceRecognitionService::class.java).apply {
                action = ACTION_STOP_RECOGNITION
            }
            context.startService(intent)
        }
        
        /**
         * 停止服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, VoiceRecognitionService::class.java)
            context.stopService(intent)
        }
    }
    
    // ========== 组件 ==========
    
    private lateinit var asrManager: ASRManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var notificationManager: NotificationManager
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // ========== 状态 ==========
    
    private var isServiceInitialized = false
    private var isRecognizing = false
    
    // ========== Service生命周期 ==========
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceRecognitionService创建")
        
        // 初始化组件
        preferencesManager = PreferencesManager(this)
        asrManager = ASRManager(this, preferencesManager)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 初始化ASR
        initializeASR()
        
        isServiceInitialized = true
        Log.d(TAG, "VoiceRecognitionService初始化完成")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        intent?.action?.let { action ->
            when (action) {
                ACTION_START_RECOGNITION -> startRecognition()
                ACTION_STOP_RECOGNITION -> stopRecognition()
                ACTION_NOTIFICATION_STOP -> stopSelf()
            }
        }
        
        // 如果服务被杀死，自动重启
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceRecognitionService销毁")
        
        // 停止识别
        stopRecognition()
        
        // 释放ASR资源
        if (isServiceInitialized) {
            asrManager.release()
        }
        
        // 取消协程
        scope.cancel()
        
        Log.d(TAG, "VoiceRecognitionService已销毁")
    }
    
    // ========== ASR初始化 ==========
    
    /**
     * 初始化ASR识别器
     */
    private fun initializeASR() {
        scope.launch(Dispatchers.IO) {
            try {
                // 获取模型路径
                val modelPath = preferencesManager.getAsrModelPath()
                
                if (modelPath == null) {
                    Log.e(TAG, "ASR模型路径未配置")
                    broadcastError("ASR模型路径未配置")
                    return@launch
                }
                
                Log.d(TAG, "初始化ASR，模型路径: $modelPath")
                
                // 初始化ASR
                val success = asrManager.initialize(modelPath)
                
                if (success) {
                    Log.d(TAG, "ASR初始化成功")
                } else {
                    Log.e(TAG, "ASR初始化失败")
                    broadcastError("ASR初始化失败")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "ASR初始化异常", e)
                broadcastError("ASR初始化异常: ${e.message}")
            }
        }
    }
    
    // ========== 识别控制 ==========
    
    /**
     * 开始识别
     */
    private fun startRecognition() {
        Log.d(TAG, "开始识别")
        
        if (isRecognizing) {
            Log.w(TAG, "已经在识别中")
            return
        }
        
        // 启动前台服务
        startForeground(SERVICE_ID, createNotification("正在监听..."))
        
        // 开始ASR识别
        asrManager.startRecognition(object : ASRManager.RecognitionCallback {
            override fun onResult(text: String, isFinal: Boolean) {
                Log.d(TAG, "识别结果: $text, isFinal: $isFinal")
                
                // 广播识别结果
                broadcastResult(text, isFinal)
                
                // 更新通知
                if (isFinal) {
                    updateNotification("识别: $text")
                }
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "识别错误: $error")
                broadcastError(error)
                updateNotification("识别错误: $error")
            }
            
            override fun onStart() {
                Log.d(TAG, "识别开始")
                isRecognizing = true
                updateNotification("正在监听...")
            }
            
            override fun onEnd() {
                Log.d(TAG, "识别结束")
                isRecognizing = false
                updateNotification("监听结束")
            }
        })
    }
    
    /**
     * 停止识别
     */
    private fun stopRecognition() {
        Log.d(TAG, "停止识别")
        
        if (!isRecognizing) {
            return
        }
        
        // 停止ASR识别
        asrManager.stopRecognition()
        
        isRecognizing = false
        
        // 更新通知
        updateNotification("已停止监听")
    }
    
    // ========== 通知管理 ==========
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "通知渠道创建成功")
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(text: String): Notification {
        // 创建点击Intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 创建停止Intent
        val stopIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = ACTION_NOTIFICATION_STOP
        }
        
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 构建通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音待办")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPendingIntent
            )
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        notificationManager.notify(SERVICE_ID, notification)
    }
    
    // ========== 广播 ==========
    
    /**
     * 广播识别结果
     */
    private fun broadcastResult(text: String, isFinal: Boolean) {
        val intent = Intent(ACTION_RECOGNITION_RESULT).apply {
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_IS_FINAL, isFinal)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 广播错误
     */
    private fun broadcastError(error: String) {
        val intent = Intent(ACTION_RECOGNITION_ERROR).apply {
            putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
    }
}
