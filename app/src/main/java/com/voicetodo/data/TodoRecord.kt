package com.voicetodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办事项记录实体类
 * 用于存储用户通过语音生成的待办事项
 */
@Entity(tableName = "todo_records")
data class TodoRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 原始语音识别文本
     */
    val originalText: String,
    
    /**
     * LLM生成的摘要
     */
    val summary: String,
    
    /**
     * 解析后的待办事项列表（JSON格式）
     * 格式示例: [{"text":"完成项目报告","completed":false},{"text":"发送邮件","completed":false}]
     */
    val todosJson: String,
    
    /**
     * 记录创建时间戳（毫秒）
     */
    val timestamp: Long = System.currentTimeMillis()
)
