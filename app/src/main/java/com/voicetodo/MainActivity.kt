package com.voicetodo

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicetodo.asr.ASRManager
import com.voicetodo.data.TodoItem
import com.voicetodo.data.TodoRecord
import com.voicetodo.data.TodoRepository
import com.voicetodo.llm.LLMManager
import com.voicetodo.processor.TextProcessor
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面Activity
 * 实现语音录制、实时转写、结果展示和导航功能
 */
class MainActivity : AppCompatActivity() {

    private lateinit var asrManager: ASRManager
    private lateinit var llmManager: LLMManager
    private lateinit var textProcessor: TextProcessor
    private lateinit var todoRepository: TodoRepository
    
    private lateinit var btnRecord: Button
    private lateinit var tvRealtimeText: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvTodosTitle: TextView
    private lateinit var rvTodos: RecyclerView
    private lateinit var btnHistory: Button
    private lateinit var btnSettings: Button
    
    private lateinit var todoAdapter: TodoAdapter
    
    private var currentRecognizedText = ""
    private var isRecording = false

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "需要录音和存储权限才能使用应用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化管理器
        val app = application as VoiceToDoApplication
        todoRepository = app.todoRepository
        asrManager = ASRManagerManager.getInstance().getASRManager(this)
        llmManager = LLMManager(app.preferencesManager)
        textProcessor = TextProcessor(llmManager)

        // 初始化UI
        initViews()

        // 请求权限
        requestPermissions()
    }

    private fun initViews() {
        // 录音按钮
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }

        // 实时转写文本
        tvRealtimeText = findViewById(R.id.tvRealtimeText)
        
        // 状态显示
        tvStatus = findViewById(R.id.tvStatus)
        
        // 结果区域
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvSummary = findViewById(R.id.tvSummary)
        tvTodosTitle = findViewById(R.id.tvTodosTitle)
        
        // 待办事项列表
        rvTodos = findViewById(R.id.rvTodos)
        rvTodos.layoutManager = LinearLayoutManager(this)
        todoAdapter = TodoAdapter { position, isChecked ->
            // 处理待办事项勾选（可选：保存状态）
        }
        rvTodos.adapter = todoAdapter

        // 导航按钮
        btnHistory = findViewById(R.id.btnHistory)
        btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            val intent = Intent(this, ModelManagementActivity::class.java)
            startActivity(intent)
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        requestPermissionLauncher.launch(permissions)
    }

    private fun startRecording() {
        if (isRecording) return
        
        isRecording = true
        btnRecord.text = "松开结束"
        btnRecord.backgroundTintList = getColorStateList(R.color.red)
        tvStatus.text = "正在录音..."
        currentRecognizedText = ""
        tvRealtimeText.text = ""
        
        // 隐藏结果区域
        hideResultArea()

        try {
            asrManager.startRecognition(object : ASRManager.RecognitionCallback {
                override fun onResult(text: String, isFinal: Boolean) {
                    runOnUiThread {
                        currentRecognizedText = text
                        tvRealtimeText.text = text
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "识别错误: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "启动录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
            btnRecord.text = "按住录音"
            btnRecord.backgroundTintList = getColorStateList(R.color.primary_color)
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        btnRecord.text = "按住录音"
        btnRecord.backgroundTintList = getColorStateList(R.color.primary_color)
        tvStatus.text = "处理中..."
        
        try {
            asrManager.stopRecognition()
            
            // 处理识别结果
            if (currentRecognizedText.isNotEmpty()) {
                processRecognizedText()
            } else {
                tvStatus.text = "状态：空闲"
                Toast.makeText(this, "未识别到语音", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "停止录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            tvStatus.text = "状态：空闲"
        }
    }

    private fun processRecognizedText() {
        lifecycleScope.launch {
            try {
                tvStatus.text = "正在处理..."
                
                // 使用TextProcessor处理文本
                val result = withContext(Dispatchers.Default) {
                    textProcessor.processText(currentRecognizedText) { progress ->
                        runOnUiThread {
                            tvStatus.text = "处理中: $progress"
                        }
                    }
                }
                
                // 显示结果
                withContext(Dispatchers.Main) {
                    showResult(result)
                    
                    // 保存到数据库
                    saveToDatabase(currentRecognizedText, result)
                    
                    tvStatus.text = "状态：空闲"
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    tvStatus.text = "状态：空闲"
                }
            }
        }
    }

    private fun showResult(result: TextProcessor.ProcessResult) {
        // 显示结果区域
        tvResultTitle.visibility = View.VISIBLE
        tvSummary.visibility = View.VISIBLE
        tvTodosTitle.visibility = View.VISIBLE
        rvTodos.visibility = View.VISIBLE
        
        // 显示摘要
        tvSummary.text = result.summary
        
        // 显示待办事项列表
        todoAdapter.submitList(result.todos)
    }

    private fun hideResultArea() {
        tvResultTitle.visibility = View.GONE
        tvSummary.visibility = View.GONE
        tvTodosTitle.visibility = View.GONE
        rvTodos.visibility = View.GONE
    }

    private suspend fun saveToDatabase(originalText: String, result: TextProcessor.ProcessResult) {
        try {
            val gson = Gson()
            val todosJson = gson.toJson(result.todos)
            
            val record = TodoRecord(
                originalText = originalText,
                summary = result.summary,
                todosJson = todosJson,
                timestamp = System.currentTimeMillis()
            )
            
            todoRepository.insert(record)
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            asrManager.release()
            llmManager.destroy()
        } catch (e: Exception) {
            // 忽略释放时的异常
        }
    }
}
