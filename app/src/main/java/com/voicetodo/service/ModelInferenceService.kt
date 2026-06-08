package com.voicetodo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voicetodo.R
import com.voicetodo.data.PreferencesManager
import com.voicetodo.llm.LLMManager
import com.voicetodo.llm.ModelDownloader
import com.voicetodo.llm.ModelInfo
import com.voicetodo.llm.DownloadProgress
import com.voicetodo.llm.DownloadResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 模型推理推理服务
 * 
 * 作为前台服务运行，负责：
 * 1. 管理LLM模型下载
 * 2. 管理LLM模型加载和推理
 * 3. 提供异步推理接口
 */
class ModelInferenceService : Service() {
    companion object {
        private const val TAG = "ModelInferenceService"
        
        // 服务操作
        const val ACTION_START_SERVICE = "com.voicetodo.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.voicetodo.action.STOP_SERVICE"
        const val ACTION_DOWNLOAD_MODEL = "com.voicetodo.action.DOWNLOAD_MODEL"
        const val ACTION_INFERENCE = "com.voicetodo.action.INFERENCE"
        
        // 广播Action
        const val BROADCAST_DOWNLOAD_PROGRESS = "com.voicetodo.broadcast.DOWNLOAD_PROGRESS"
        const val BROADCAST_DOWNLOAD_RESULT = "com.voicetodo.broadcast.DOWNLOAD_RESULT"
        const val BROADCAST_INFERENCE_RESULT = "com.voicetodo.broadcast.INFERENCE_RESULT"
        const val BROADCAST_INFERENCE_PROGRESS = "com.voicetodo.broadcast.INFERENCE_PROGRESS"
        
        // 额外数据
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_MODEL_URL = "model_url"
        const val EXTRA_MODEL_SIZE = "model_size"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_DOWNLOADED = "downloaded"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_RESULT = "result"
        const val EXTRA_ERROR = "error"
        const val EXTRA_IS_COMPLETED = "is_completed"
        
        // 通知
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "model_inference_channel"
        private const val CHANNEL_NAME = "模型推理服务"
    }
    
    // Binder
    private val binder = LocalBinder()
    
    // LLM管理器
    private lateinit var llmManager: LLMManager
    
    // 模型下载器
    private lateinit var modelDownloader: ModelDownloader
    
    // 配置管理器
    private lateinit var preferencesManager: PreferencesManager
    
    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 服务状态
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    // 下载状态
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    /**
     * 本地Binder，用于Activity绑定服务
     */
    inner class LocalBinder : Binder() {
        fun getService(): ModelInferenceService = this@ModelInferenceService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        
        // 初始化组件
        preferencesManager = PreferencesManager(this)
        llmManager = LLMManager(this, preferencesManager)
        modelDownloader = ModelDownloader(this)
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到命令: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                Log.d(TAG, "启动服务")
                _serviceState.value = ServiceState.Running
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "停止服务")
                stopSelf()
            }
            ACTION_DOWNLOAD_MODEL -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)
                val modelUrl = intent.getStringExtra(EXTRA_MODEL_URL)
                val modelSize = intent.getLongExtra(EXTRA_MODEL_SIZE, 0)
                
                if (modelName != null && modelUrl != null && modelSize > 0) {
                    downloadModel(modelName, modelUrl, modelSize)
                }
            }
            ACTION_INFERENCE -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT)
                if (prompt != null) {
                    performInference(prompt)
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "绑定服务")
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        
        // 释放资源
        llmManager.destroy()
        modelDownloader.destroy()
        serviceScope.cancel()
        
        _serviceState.value = ServiceState.Stopped
    }
    
    /**
     * 下载模型
     * 
     * @param modelName 模型名称
     * @param modelUrl 模型下载URL
     * @param modelSize 模型期望大小
     */
    private fun downloadModel(modelName: String, modelUrl: String, modelSize: Long) {
        Log.d(TAG, "开始下载模型: $modelName")
        
        _downloadState.value = DownloadState.Downloading(0f)
        
        serviceScope.launch {
            try {
                // 构建本地保存路径
                val modelsDir = File(filesDir, "models")
                val modelFile = File(modelsDir, modelName)
                
                val modelInfo = ModelInfo(
                    name = modelName,
                    downloadUrl = modelUrl,
                    localPath = modelFile.absolutePath,
                    expectedSize = modelSize
                )
                
                // 执行下载
                val result = modelDownloader.downloadModel(
                    modelInfo = modelInfo,
                    onProgress = { progress ->
                        // 更新下载状态
                        _downloadState.value = DownloadState.Downloading(progress.progress)
                        
                        // 发送进度广播
                        sendDownloadProgressBroadcast(progress)
                    }
                )
                
                // 处理下载结果
                when (result) {
                    is DownloadResult.Success -> {
                        Log.d(TAG, "模型下载成功: ${result.filePath}")
                        _downloadState.value = DownloadState.Completed(result.filePath)
                        
                        // 发送成功广播
                        sendDownloadResultBroadcast(true, result.filePath, null)
                        
                        // 自动加载模型
                        llmManager.switchModel(result.filePath)
                    }
                    is DownloadResult.Error -> {
                        Log.e(TAG, "模型下载失败: ${result.message}")
                        _downloadState.value = DownloadState.Error(result.message)
                        
                        // 发送失败广播
                        sendDownloadResultBroadcast(false, null, result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载模型时发生异常", e)
                _downloadState.value = DownloadState.Error("下载异常: ${e.message}")
                sendDownloadResultBroadcast(false, null, "下载异常: ${e.message}")
            }
        }
    }
    
    /**
     * 执行推理
     * 
     * @param prompt 输入提示词
     */
    private fun performInference(prompt: String) {
        Log.d(TAG, "开始推理，prompt长度: ${prompt.length}")
        
        serviceScope.launch {
            try {
                // 检查LLM是否已初始化
                if (!llmManager.isReady()) {
                    Log.w(TAG, "LLM未就绪，尝试初始化")
                    val initialized = llmManager.initialize()
                    if (!initialized) {
                        Log.e(TAG, "LLM初始化失败")
                                               sendInferenceResultBroadcast(false, null, "LLM初始化失败")
                        return@launch
                    }
                }
                
                // 执行推理
                val result = llmManager.sendMessageAsync(
                    prompt = prompt,
                    onProgress = { partialResult ->
                        // 发送推理进度广播
                        sendInferenceProgressBroadcast(partialResult)
                    }
                )
                
                Log.d(TAG, "推理完成，结果长度: ${result.length}")
                
                // 发送推理结果广播
                sendInferenceResultBroadcast(true, result, null)
                
            } catch (e: Exception) {
                Log.e(TAG, "推理时发生异常", e)
                sendInferenceResultBroadcast(false, null, "推理异常: ${e.message}")
            }
        }
    }
    
    /**
     * 发送下载进度广播
     */
    private fun sendDownloadProgressBroadcast(progress: DownloadProgress) {
        val intent = Intent(BROADCAST_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_MODEL_NAME, progress.modelName)
            putExtra(EXTRA_DOWNLOADED, progress.downloadedBytes)
            putExtra(EXTRA_TOTAL, progress.totalBytes)
            putExtra(EXTRA_PROGRESS, progress.progress)
            putExtra(EXTRA_IS_COMPLETED, progress.isCompleted)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 发送下载结果广播
     */
    private fun sendDownloadResultBroadcast(success: Boolean, filePath: String?, error: String?) {
        val intent = Intent(BROADCAST_DOWNLOAD_RESULT).apply {
            putExtra(EXTRA_RESULT, if (success) filePath else error)
            putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 发送推理进度广播
     */
    private fun sendInferenceProgressBroadcast(partialResult: String) {
        val intent = Intent(BROADCAST_INFERENCE_PROGRESS).apply {
            putExtra(EXTRA_RESULT, partialResult)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 发送推理结果广播
     */
    private fun sendInferenceResultBroadcast(success: Boolean, result: String?, error: String?) {
        val intent = Intent(BROADCAST_INFERENCE_RESULT).apply {
            putExtra(EXTRA_RESULT, if (success) result else error)
            putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
    }
    
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
                description = "模型推理服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, ModelInferenceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("模型推理服务")
            .setContentText("服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                pendingIntent
            )
            .build()
    }
    
    /**
     * 获取LLM管理器
     */
    fun getLLMManager(): LLMManager = llmManager
    
    /**
     * 获取模型下载器
     */
    fun getModelDownloader(): ModelDownloader = modelDownloader
}

/**
 * 服务状态
 */
sealed class ServiceState {
    data object Idle : ServiceState()
    data object Running : ServiceState()
    data object Stopped : ServiceState()
}

/**
 * 下载状态
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
