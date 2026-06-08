package com.voicetodo.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型下载器
 * 
 * 支持HTTP断点续传、CDN重定向、文件完整性校验
 * 
 * @property context Android上下文
 */
class ModelDownloader(private val context: Context) {
    companion object {
        private const val TAG = "ModelDownloader"
        
        // 下载超时时间（毫秒）
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
        
        // 缓冲区大小
        private const val BUFFER_SIZE = 8192
        
        // 最大重定向次数
        private const val MAX_REDIRECTS = 5
    }
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 下载模型
     * 
     * @param modelInfo 模型信息
     * @param onProgress 下载进度回调
     * @return 下载结果
     */
    suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        val outputFile = File(modelInfo.localPath)
        
        try {
            Log.d(TAG, "开始下载模型: ${modelInfo.name}")
            Log.d(TAG, "下载URL: ${modelInfo.downloadUrl}")
            Log.d(TAG, "保存路径: ${modelInfo.localPath}")
            Log.d(TAG, "期望大小: ${modelInfo.expectedSize} bytes")
            
            // 创建输出目录
            outputFile.parentFile?.mkdirs()
            
            // 检查是否支持断点续传
            val downloadedBytes = if (outputFile.exists()) {
                outputFile.length()
            } else {
                0L
            }
            
            Log.d(TAG, "已下载: $downloadedBytes bytes")
            
            // 如果文件已完整下载，则跳过
            if (downloadedBytes > 0 && downloadedBytes == modelInfo.expectedSize) {
                Log.d(TAG, "文件已完整下载，跳过")
                onProgress?.invoke(DownloadProgress(
                    modelInfo.name,
                    modelInfo.expectedSize,
                    modelInfo.expectedSize,
                    1.0f,
                    true
                ))
                return@withContext DownloadResult.Success(outputFile.absolutePath)
            }
            
            // 执行下载
            downloadWithRetry(
                url = modelInfo.downloadUrl,
                outputFile = outputFile,
                expectedSize = modelInfo.expectedSize,
                downloadedBytes = downloadedBytes,
                onProgress = onProgress,
                redirectCount = 0
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "下载模型失败", e)
            
            // 删除不完整的文件
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            DownloadResult.Error("下载失败: ${e.message}")
        }
    }
    
    /**
     * 带重试的下载
     */
    private suspend fun downloadWithRetry(
        url: String,
        outputFile: File,
        expectedSize: Long,
        downloadedBytes: Long,
        onProgress: ((DownloadProgress) -> Unit)?,
        redirectCount: Int
    ): DownloadResult {
        // 检查重定向次数
        if (redirectCount > MAX_REDIRECTS) {
            return DownloadResult.Error("重定向次数超过限制")
        }
        
        val connection = try {
            val urlObj = URL(url)
            urlObj.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return DownloadResult.Error("连接失败: ${e.message}")
        }
        
        try {
            // 配置连接
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = false // 手动处理重定向
            
            // 支持断点续传
            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                Log.d(TAG, "启用断点续传，从 $downloadedBytes 开始")
            }
            
            // 连接
            connection.connect()
            
            // 检查响应码
            val responseCode = connection.responseCode
            Log.d(TAG, "响应码: $responseCode")
            
            // 处理重定向
            if (responseCode in 300..399) {
                val newUrl = connection.getHeaderField("Location")
                Log.d(TAG, "重定向到: $newUrl")
                connection.disconnect()
                return downloadWithRetry(
                    url = newUrl ?: url,
                    outputFile = outputFile,
                    expectedSize = expectedSize,
                    downloadedBytes = downloadedBytes,
                    onProgress = onProgress,
                    redirectCount = redirectCount + 1
                )
            }
            
            // 检查是否成功
            if (responseCode !in 200..206) {
                return DownloadResult.Error("HTTP错误: $responseCode")
            }
            
            // 获取文件大小
            val contentLength = connection.contentLengthLong
            val totalSize = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                expectedSize
            } else {
                contentLength
            }
            
            Log.d(TAG, "总大小: $totalSize bytes")
            
            // 打开输出流
            val outputStream = if (downloadedBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL) {
                FileOutputStream(outputFile, true) // 追加模式
            } else {
                FileOutputStream(outputFile) // 覆盖模式
            }
            
            // 读取数据
            val inputStream = connection.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var totalRead = downloadedBytes
            var read: Int
            
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                totalRead += read
                
                // 回调进度
                val progress = totalRead.toFloat() / totalSize
                onProgress?.invoke(DownloadProgress(
                    modelInfo.name,
                    totalRead,
                    totalSize,
                    progress,
                    false
                ))
            }
            
            // 关闭流
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            // 验证文件完整性
            val actualSize = outputFile.length()
            Log.d(TAG, "实际下载大小: $actualSize bytes")
            Log.d(TAG, "期望大小: $expectedSize bytes")
            
            if (actualSize != expectedSize) {
                Log.e(TAG, "文件大小不匹配，删除文件")
                outputFile.delete()
                return DownloadResult.Error("文件完整性校验失败: 期望 $expectedSize, 实际 $actualSize")
            }
            
            Log.d(TAG, "下载成功")
            DownloadResult.Success(outputFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "下载过程中发生异常", e)
            DownloadResult.Error("下载异常: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 取消下载
     */
    fun cancel() {
        scope.cancel()
    }
    
    /**
     * 销毁下载器
     */
    fun destroy() {
        scope.cancel()
    }
}

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val downloadUrl: String,
    val localPath: String,
    val expectedSize: Long,
    val checksum: String? = null,
    val version: String = "1.0"
)

/**
 * 下载进度
 */
data class DownloadProgress(
    val modelName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float, // 0.0 - 1.0
    val isCompleted: Boolean
)

/**
 * 下载结果
 */
sealed class DownloadResult {
    data class Success(val filePath: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}
