package com.voicetodo

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicetodo.data.PreferencesManager
import com.voicetodo.llm.LLMManager
import com.voicetodo.llm.ModelDownloader
import kotlinx.coroutines.launch
import java.io.File

/**
 * 模型管理Activity
 * 展示可用模型列表，支持下载和切换模型
 */
class ModelManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var modelAdapter: ModelAdapter
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var llmManager: LLMManager
    
    private val models = listOf(
        ModelInfo(
            id = "gemma3-4b",
            name = "Gemma 3 4B",
            description = "Google Gemma 3 4B 模型，适合日常任务",
            size = "4.8 GB",
            memoryRequirement = "6 GB RAM",
            downloadUrl = "https://example.com/models/gemma3-4b.lite",
            expectedSize = 4.8 * 1024 * 1024 * 1024L
        ),
        ModelInfo(
            id = "qwen2.5-7b",
            name = "Qwen 2.5 7B",
            description = "阿里通义千问 2.5 7B 模型，性能更强",
            size = "8.2 GB",
            memoryRequirement = "10 GB RAM",
            downloadUrl = "https://example.com/models/qwen2.5-7b.lite",
            expectedSize = 8.2 * 1024 * 1024 * 1024L
        ),
        ModelInfo(
            id = "phi-3-4b",
            name = "Phi-3 4B",
            description = "Microsoft Phi-3 4B 模型，轻量高效",
            size = "4.5 GB",
            memoryRequirement = "6 GB RAM",
            downloadUrl = "https://example.com/models/phi3-4b.lite",
            expectedSize = 4.5 * 1024 * 1024 * 1024L
        ),
        ModelInfo(
            id = "llama3.2-3b",
            name = "Llama 3.2 3B",
            description = "Meta Llama 3.2 3B 模型，快速响应",
            size = "3.8 GB",
            memoryRequirement = "5 GB RAM",
            downloadUrl = "https://example.com/models/llama3.2-3b.lite",
            expectedSize = 3.8 * 1024 * 1024 * 1024L
        )
    )

    private val STORAGE_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_management)

        // 初始化管理器
        preferencesManager = PreferencesManager(this)
        llmManager = LLMManager(preferencesManager)

        // 初始化UI
        initViews()

        // 检查存储权限
        checkStoragePermission()
    }

    private fun initViews() {
        // 设置标题
        title = "模型管理"

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 获取当前激活的模型
        val currentModelPath = preferencesManager.getLlmModelPath()
        
        modelAdapter = ModelAdapter(
            models = models,
            currentModelPath = currentModelPath,
            onDownloadClick = { model ->
                downloadModel(model)
            },
            onSwitchClick = { model ->
                switchModel(model)
            },
            onDeleteClick = { model ->
                deleteModel(model)
            }
        )
        recyclerView.adapter = modelAdapter
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要存储权限才能下载模型", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadModel(model: ModelInfo) {
        // 确认下载
        AlertDialog.Builder(this)
            .setTitle("下载模型")
            .setMessage("确定要下载 ${model.name} 吗？\n大小：${model.size}\n内存要求：${model.memoryRequirement}")
            .setPositiveButton("确定") { _, _ ->
                performDownload(model)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performDownload(model: ModelInfo) {
        lifecycleScope.launch {
            try {
                // 更新UI状态为下载中
                modelAdapter.updateDownloadStatus(model.id, true, 0.0f)

                // 创建下载器
                val downloader = ModelDownloader()
                val modelDir = File(filesDir, "models")
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }
                val modelFile = File(modelDir, "${model.id}.lite")

                // 开始下载
                downloader.downloadModel(
                    url = model.downloadUrl,
                    outputFile = modelFile,
                    expectedSize = model.expectedSize,
                    onProgress = { progress ->
                        runOnUiThread {
                            modelAdapter.updateDownloadStatus(model.id, true, progress)
                        }
                    }
                )

                // 下载成功
                runOnUiThread {
                    modelAdapter.updateDownloadStatus(model.id, false, 1.0f)
                    Toast.makeText(this@ModelManagementActivity, "模型下载成功", Toast.LENGTH_SHORT).show()
                    
                    // 询问是否切换到新模型
                    AlertDialog.Builder(this@ModelManagementActivity)
                        .setTitle("下载完成")
                        .setMessage("模型已下载完成，是否切换到 ${model.name}？")
                        .setPositiveButton("切换") { _, _ ->
                            switchModel(model)
                        }
                        .setNegativeButton("稍后", null)
                        .show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    modelAdapter.updateDownloadStatus(model.id, false, 0.0f)
                    Toast.makeText(this@ModelManagementActivity, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun switchModel(model: ModelInfo) {
        val modelFile = File(filesDir, "models/${model.id}.lite")
        
        if (!modelFile.exists()) {
            Toast.makeText(this, "模型文件不存在，请先下载", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // 切换模型
                llmManager.switchModel(modelFile.absolutePath)
                
                runOnUiThread {
                    Toast.makeText(this@ModelManagementActivity, "已切换到 ${model.name}", Toast.LENGTH_SHORT).show()
                    
                    // 更新当前模型状态
                    modelAdapter.updateCurrentModel(modelFile.absolutePath)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ModelManagementActivity, "切换失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteModel(model: ModelInfo) {
        AlertDialog.Builder(this)
            .setTitle("删除模型")
            .setMessage("确定要删除 ${model.name} 吗？")
            .setPositiveButton("确定") { _, _ ->
                val modelFile = File(filesDir, "models/${model.id}.lite")
                if (modelFile.exists()) {
                    modelFile.delete()
                    Toast.makeText(this, "模型已删除", Toast.LENGTH_SHORT).show()
                    modelAdapter.updateDownloadStatus(model.id, false, 0.0f)
                } else {
                    Toast.makeText(this, "模型文件不存在", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 模型信息数据类
     */
    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String,
        val size: String,
        val memoryRequirement: String,
        val downloadUrl: String,
        val expectedSize: Long
    )

    /**
     * 模型列表适配器
     */
    class ModelAdapter(
        private val models: List<ModelInfo>,
        private var currentModelPath: String?,
        private val onDownloadClick: (ModelInfo) -> Unit,
        private val onSwitchClick: (ModelInfo) -> Unit,
        private val onDeleteClick: (ModelInfo) -> Unit
    ) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

        private val downloadStatus = mutableMapOf<String, Pair<Boolean, Float>>() // (isDownloading, progress)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_model, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(models[position])
        }

        override fun getItemCount(): Int = models.size

        fun updateDownloadStatus(modelId: String, isDownloading: Boolean, progress: Float) {
            downloadStatus[modelId] = isDownloading to progress
            val position = models.indexOfFirst { it.id == modelId }
            if (position != -1) {
                notifyItemChanged(position)
            }
        }

        fun updateCurrentModel(path: String?) {
            currentModelPath = path
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvModelName: TextView = itemView.findViewById(R.id.tvModelName)
            private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
            private val tvSize: TextView = itemView.findViewById(R.id.tvSize)
            private val tvMemory: TextView = itemView.findViewById(R.id.tvMemory)
            private val btnDownload: View = itemView.findViewById(R.id.btnDownload)
            private val btnSwitch: View = itemView.findViewById(R.id.btnSwitch)
            private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
            private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
            private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
            private val tvCurrent: TextView = itemView.findViewById(R.id.tvCurrent)

            fun bind(model: ModelInfo) {
                tvModelName.text = model.name
                tvDescription.text = model.description
                tvSize.text = "大小：${model.size}"
                tvMemory.text = "内存要求：${model.memoryRequirement}"

                // 检查模型是否已下载
                val modelFile = File(itemView.context.filesDir, "models/${model.id}.lite")
                val isDownloaded = modelFile.exists()
                val isCurrentModel = currentModelPath == modelFile.absolutePath

                // 更新UI状态
                val (isDownloading, progress) = downloadStatus[model.id] ?: (false to 0f)

                if (isCurrentModel) {
                    tvCurrent.visibility = View.VISIBLE
                    btnDownload.visibility = View.GONE
                    btnSwitch.visibility = View.GONE
                    btnDelete.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                } else if (isDownloading) {
                    tvCurrent.visibility = View.GONE
                    btnDownload.visibility = View.GONE
                    btnSwitch.visibility = View.GONE
                    btnDelete.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    tvProgress.visibility = View.VISIBLE
                    progressBar.progress = (progress * 100).toInt()
                    tvProgress.text = "${(progress * 100).toInt()}%"
                } else if (isDownloaded) {
                    tvCurrent.visibility = View.GONE
                    btnDownload.visibility = View.GONE
                    btnSwitch.visibility = View.VISIBLE
                    btnDelete.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                } else {
                    tvCurrent.visibility = View.GONE
                    btnDownload.visibility = View.VISIBLE
                    btnSwitch.visibility = View.GONE
                    btnDelete.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                }

                // 设置点击事件
                btnDownload.setOnClickListener { onDownloadClick(model) }
                btnSwitch.setOnClickListener { onSwitchClick(model) }
                btnDelete.setOnClickListener { onDeleteClick(model) }
            }
        }
    }
}
