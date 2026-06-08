package com.voicetodo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicetodo.data.TodoRecord
import com.voicetodo.data.TodoRepository
import com.voicetodo.data.TodoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史记录Activity
 * 展示所有语音生成的待办记录
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var todoRepository: TodoRepository
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // 初始化Repository
        todoRepository = (application as VoiceToDoApplication).todoRepository

        // 初始化UI
        initViews()

        // 加载历史记录
        loadHistoryRecords()
    }

    private fun initViews() {
        // 设置标题
        title = "历史记录"

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter { record ->
            // 点击记录项的处理（可选：显示详情）
            showRecordDetail(record)
        }
        recyclerView.adapter = historyAdapter

        // 空状态提示
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun loadHistoryRecords() {
        lifecycleScope.launch {
            val records = todoRepository.getAllOnce()
            
            runOnUiThread {
                if (records.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    historyAdapter.submitList(records)
                }
            }
        }
    }

    private fun showRecordDetail(record: TodoRecord) {
        // 解析待办事项列表
        val gson = Gson()
        val todoType = object : TypeToken<List<TodoItem>>() {}.type
        val todos = try {
            gson.fromJson<List<TodoItem>>(record.todosJson, todoType)
        } catch (e: Exception) {
            emptyList()
        }

        // 构建详情信息
        val detail = StringBuilder()
        detail.append("原始文本：\n${record.originalText}\n\n")
        detail.append("摘要：\n${record.summary}\n\n")
        detail.append("待办事项：\n")
        todos.forEachIndexed { index, todo ->
            detail.append("${index + 1}. ${if (todo.completed) "[✓]" else "[ ]"} ${todo.text}\n")
        }
        detail.append("\n时间：${formatTimestamp(record.timestamp)}")

        // 显示详情（这里简化处理，实际可以使用AlertDialog）
        android.app.AlertDialog.Builder(this)
            .setTitle("记录详情")
            .setMessage(detail.toString())
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ ->
                deleteRecord(record)
            }
            .show()
    }

    private fun deleteRecord(record: TodoRecord) {
        lifecycleScope.launch {
            todoRepository.delete(record)
            runOnUiThread {
                loadHistoryRecords()
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 历史记录适配器
     */
    class HistoryAdapter(
        private val onItemClick: (TodoRecord) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private var records: List<TodoRecord> = emptyList()

        fun submitList(newRecords: List<TodoRecord>) {
            records = newRecords
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(records[position])
        }

        override fun getItemCount(): Int = records.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvSummary: TextView = itemView.findViewById(R.id.tvSummary)
            private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
            private val tvTodoCount: TextView = itemView.findViewById(R.id.tvTodoCount)

            fun bind(record: TodoRecord) {
                // 显示摘要（截取前100个字符）
                val summary = if (record.summary.length > 100) {
                    record.summary.substring(0, 100) + "..."
                } else {
                    record.summary
                }
                tvSummary.text = summary

                // 显示时间戳
                tvTimestamp.text = formatTimestamp(record.timestamp)

                // 计算待办事项数量
                val gson = Gson()
                val todoType = object : TypeToken<List<TodoItem>>() {}.type
                val todos = try {
                    gson.fromJson<List<TodoItem>>(record.todosJson, todoType)
                } catch (e: Exception) {
                    emptyList()
                }
                tvTodoCount.text = "${todos.size} 项待办"

                // 点击事件
                itemView.setOnClickListener {
                    onItemClick(record)
                }
            }

            private fun formatTimestamp(timestamp: Long): String {
                val sdf = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.getDefault())
                return sdf.format(Date(timestamp))
            }
        }
    }
}
