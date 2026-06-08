package com.voicetodo.processor

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.voicetodo.data.TodoItem
import com.voicetodo.llm.LLMManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 文本处理器
 * 
 * 负责连接ASR和LLM，完成以下功能：
 * 1. 标点恢复（调用CT-Transformer模型，处理降级情况）
 * 2. Prompt组装，根据系统提示词构建请求
 * 3. 结果解析，提取JSON主体并进行反序列化，实现降级策略
 * 
 * @property context Android上下文
 * @property llmManager LLM管理器
 */
class TextProcessor(
    private val context: Context,
    private val llmManager: LLMManager
) {
    
    companion object {
        private const val TAG = "TextProcessor"
        
        // 系统提示词
        private const val SYSTEM_PROMPT = """你是一个智能待办事项助手。请将用户的语音输入转换为结构化的待办事项列表。

要求：
1. 分析用户的语音内容，提取出所有待办事项
2. 为每个待办事项生成简洁清晰的描述
3. 识别任务之间的逻辑关系和优先级
4. 生成简短的摘要总结用户的意图

输出格式（必须是纯JSON，不要包含任何其他文字）：
{
  "summary": "用户意图的简短摘要",
  "todos": [
    {"text": "待办事项1", "completed": false},
    {"text": "待办事项2", "completed": false}
  ]
}

示例：
输入："明天上午九点开会，下午要完成项目报告，晚上记得给妈妈打电话"
输出：
{
  "summary": "明天有会议、项目报告和给妈妈打电话三个任务",
  "todos": [
    {"text": "明天上午九点开会", "completed": false},
    {"text": "下午完成项目报告", "completed": false},
    {"text": "晚上给妈妈打电话", "completed": false}
  ]
}

请严格按照JSON格式输出，不要添加任何说明文字。"""
        
        // JSON提取正则表达式
        private const val JSON_PATTERN = """\{[\s\S]*"summary"[\s\S]*"todos"[\s\S]*\}"""
        
        // JSON起始标记
        private const val JSON_START = "{"
        
        // JSON结束标记
        private const val JSON_END = "}"
    }
    
    // ========== 处理状态 ==========
    
    enum class ProcessingState {
        IDLE,           // 空闲
        RESTORING,      // 标点恢复中
        BUILDING,       // 构建Prompt中
        INFERRING,      // LLM推理中
        PARSING,        // 解析结果中
        ERROR           // 错误
    }
    
    private val _state = MutableStateFlow(ProcessingState.IDLE)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()
    
    // ========== CT-Transformer标点恢复 ==========
    
    private var ctTransformerAvailable = false
    private var ctTransformerModel: Any? = null // 占位，实际实现时替换为CT-Transformer的类
    
    /**
     * 初始化CT-Transformer标点恢复模型
     * 
     * @param modelPath 模型文件路径
     * @return 初始化是否成功
     */
    fun initPunctuationRestorer(modelPath: String): Boolean {
        Log.d(TAG, "初始化CT-Transformer标点恢复模型: $modelPath")
        
        try {
            // 检查模型文件是否存在
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.w(TAG, "CT-Transformer模型文件不存在: $modelPath，将使用降级策略")
                ctTransformerAvailable = false
                return false
            }
            
            // TODO: 实际集成CT-Transformer模型
            // ctTransformerModel = CTTransformer.load(modelPath)
            // ctTransformerAvailable = true
            
            // 临时方案：标记为不可用，使用降级策略
            ctTransformerAvailable = false
            Log.w(TAG, "CT-Transformer集成待实现，当前使用降级策略")
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "初始化CT-Transformer失败", e)
            ctTransformerAvailable = false
            return false
        }
    }
    
    /**
     * 恢复文本标点
     * 
     * @param text 原始文本（无标点）
     * @return 添加标点后的文本
     */
    suspend fun restorePunctuation(text: String): String {
        Log.d(TAG, "恢复文本标点，输入长度: ${text.length}")
        
        if (text.isEmpty()) {
            return text
        }
        
        _state.value = ProcessingState.RESTORING
        
        return try {
            if (ctTransformerAvailable && ctTransformerModel != null) {
                // 使用CT-Transformer进行标点恢复
                restorePunctuationWithModel(text)
            } else {
                // 降级策略：使用简单的规则添加标点
                restorePunctuationWithRules(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "标点恢复失败，使用原始文本", e)
            _state.value = ProcessingState.ERROR
            text // 返回原始文本
        } finally {
            _state.value = ProcessingState.IDLE
        }
    }
    
    /**
     * 使用CT-Transformer模型恢复标点
     * 
     * @param text 原始文本
     * @return 添加标点后的文本
     */
    private suspend fun restorePunctuationWithModel(text: String): String {
        // TODO: 实际调用CT-Transformer模型
        // return ctTransformerModel.addPunctuation(text)
        
        // 临时实现
        return restorePunctuationWithRules(text)
    }
    
    /**
     * 使用规则恢复标点（降级策略）
     * 
     * 策略：
     * 1. 在句子末尾添加句号
     * 2. 在"然后"、"之后"、"接着"等连接词前添加逗号
     * 3. 在"但是"、"不过"等转折词前添加逗号
     * 
     * @param text 原始文本
     * @return 添加标点后的文本
     */
    private fun restorePunctuationWithRules(text: String): String {
        var result = text.trim()
        
        // 移除末尾已有的标点
        val lastChar = result.lastOrNull()
        if (lastChar != null && lastChar in "。，！？；：") {
            result = result.dropLast(1).trim()
        }
        
        // 添加句号
        if (result.isNotEmpty()) {
            result += "。"
        }
        
        Log.d(TAG, "使用规则恢复标点: $result")
        return result
    }
    
    // ========== Prompt组装 ==========
    
    /**
     * 构建Prompt
     * 
     * @param userText 用户输入的文本
     * @return 完整的Prompt
     */
    fun buildPrompt(userText: String): String {
        Log.d(TAG, "构建Prompt，用户文本长度: ${userText.length}")
        
        _state.value = ProcessingState.BUILDING
        
        val prompt = """$SYSTEM_PROMPT

用户输入：$userText

请输出JSON格式的结果："""
        
        Log.d(TAG, "Prompt构建完成，长度: ${prompt.length}")
        _state.value = ProcessingState.IDLE
        
        return prompt
    }
    
    // ========== 结果解析 ==========
    
    /**
     * 处理结果数据类
     */
    data class ProcessResult(
        val summary: String,
        val todos: List<TodoItem>,
        val rawOutput: String,
        val usedFallback: Boolean
    )
    
    /**
     * 处理LLM输出
     * 
     * @param rawOutput LLM原始输出
     * @param originalText 原始用户文本（用于降级）
     * @return 处理结果
     */
    suspend fun processOutput(rawOutput: String, originalText: String): ProcessResult {
        Log.d(TAG, "处理LLM输出，原始输出长度: ${rawOutput.length}")
        
        _state.value = ProcessingState.PARSING
        
        return try {
            // 尝试解析JSON
            val result = parseJsonOutput(rawOutput)
            
            if (result != null) {
                Log.d(TAG, "JSON解析成功，待办事项数量: ${result.todos.size}")
                ProcessResult(
                    summary = result.summary,
                    todos = result.todos,
                    rawOutput = rawOutput,
                    usedFallback = false
                )
            } else {
                // JSON解析失败，使用降级策略
                Log.w(TAG, "JSON解析失败，使用降级策略")
                val fallbackResult = createFallbackResult(rawOutput, originalText)
                ProcessResult(
                    summary = fallbackResult.summary,
                    todos = fallbackResult.todos,
                    rawOutput = rawOutput,
                    usedFallback = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理输出失败，使用降级策略", e)
            val fallbackResult = createFallbackResult(rawOutput, originalText)
            ProcessResult(
                summary = fallbackResult.summary,
                todos = fallbackResult.todos,
                rawOutput = rawOutput,
                usedFallback = true
            )
        } finally {
            _state.value = ProcessingState.IDLE
        }
    }
    
    /**
     * 解析JSON输出
     * 
     * 策略：
     * 1. 尝试直接解析整个输出
     * 2. 如果失败，尝试提取JSON部分（使用正则或substring策略）
     * 3. 如果还是失败，返回null
     * 
     * @param rawOutput 原始输出
     * @return 解析结果，失败返回null
     */
    private fun parseJsonOutput(rawOutput: String): ParsedResult? {
        val gson = Gson()
        
        // 策略1: 直接解析
        try {
            val result = gson.fromJson(rawOutput, ParsedResult::class.java)
            if (isValidResult(result)) {
                Log.d(TAG, "策略1: 直接解析成功")
                return result
            }
        } catch (e: JsonSyntaxException) {
            Log.d(TAG, "策略1: 直接解析失败，尝试策略2")
        }
        
        // 策略2: 使用正则表达式提取JSON
        try {
            val regex = Regex(JSON_PATTERN)
            val match = regex.find(rawOutput)
            if (match != null) {
                val jsonStr = match.value
                val result = gson.fromJson(jsonStr, ParsedResult::class.java)
                if (isValidResult(result)) {
                    Log.d(TAG, "策略2: 正则提取成功")
                    return result
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "策略2: 正则提取失败，尝试策略3")
        }
        
        // 策略3: 使用substring策略
        try {
            val jsonStr = extractJsonBySubstring(rawOutput)
            if (jsonStr.isNotEmpty()) {
                val result = gson.fromJson(jsonStr, ParsedResult::class.java)
                if (isValidResult(result)) {
                    Log.d(TAG, "策略3: substring提取成功")
                    return result
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "策略3: substring提取失败")
        }
        
        // 所有策略都失败
        return null
    }
    
    /**
     * 使用substring策略提取JSON
     * 
     * 策略：
     * 1. 找到第一个 '{'
     * 2. 从该位置开始，向后查找匹配的 '}'
     * 3. 使用计数器确保找到最外层的 '}'
     * 
     * @param text 原始文本
     * @return 提取的JSON字符串
     */
    private fun extractJsonBySubstring(text: String): String {
        val startIndex = text.indexOf(JSON_START)
        if (startIndex == -1) {
            return ""
        }
        
        var braceCount = 0
        var endIndex = -1
        
        for (i in startIndex until text.length) {
            when (text[i]) {
                JSON_START[0] -> braceCount++
                JSON_END[0] -> {
                    braceCount--
                    if (braceCount == 0) {
                        endIndex = i
                        break
                    }
                }
            }
        }
        
        return if (endIndex != -1) {
            text.substring(startIndex, endIndex + 1)
        } else {
            ""
        }
    }
    
    /**
     * 验证解析结果是否有效
     * 
     * @param result 解析结果
     * @return 是否有效
     */
    private fun isValidResult(result: ParsedResult?): Boolean {
        if (result == null) return false
        if (result.summary.isEmpty()) return false
        if (result.todos.isEmpty()) return false
        // 检查todos中每个item的text不为空
        return result.todos.all { it.text.isNotEmpty() }
    }
    
    /**
     * 创建降级结果
     * 
     * 策略：
     * 1. 将原始输出作为summary
     * 2. 将原始文本作为单个待办事项
     * 
     * @param rawOutput LLM原始输出
     * @param originalText 原始用户文本
     * @return 降级结果
     */
    private fun createFallbackResult(rawOutput: String, originalText: String): ParsedResult {
        Log.d(TAG, "创建降级结果")
        
        // 清理原始输出（移除可能的JSON标记）
        val cleanedOutput = rawOutput
            .replace("```json", "")
            .replace("```", "")
            .trim()
        
        val summary = if (cleanedOutput.isNotEmpty() && cleanedOutput != originalText) {
            cleanedOutput
        } else {
            originalText
        }
        
        val todos = listOf(
            TodoItem(
                text = originalText,
                completed = false
            )
        )
        
        return ParsedResult(
            summary = summary,
            todos = todos
        )
    }
    
    // ========== 完整处理流程 ==========
    
    /**
     * 完整处理流程
     * 
     * 流程：
     * 1. 恢复标点
     * 2. 构建Prompt
     * 3. 调用LLM推理
     * 4. 解析结果
     * 
     * @param originalText 原始语音识别文本
     * @param onProgress LLM推理进度回调
     * @return 处理结果
     */
    suspend fun processText(
        originalText: String,
        onProgress: ((String) -> Unit)? = null
    ): ProcessResult {
        Log.d(TAG, "开始完整处理流程，原始文本: $originalText")
        
        return try {
            // 步骤1: 恢复标点
            val textWithPunctuation = restorePunctuation(originalText)
            Log.d(TAG, "标点恢复完成: $textWithPunctuation")
            
            // 步骤2: 构建Prompt
            val prompt = buildPrompt(textWithPunctuation)
            Log.d(TAG, "Prompt构建完成")
            
            // 步骤3: 调用LLM推理
            _state.value = ProcessingState.INFERRING
            val llmOutput = llmManager.sendMessageAsync(prompt, onProgress)
            Log.d(TAG, "LLM推理完成，输出长度: ${llmOutput.length}")
            
            // 步骤4: 解析结果
            val result = processOutput(llmOutput, originalText)
            
            Log.d(TAG, "完整处理流程完成，待办事项数量: ${result.todos.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "完整处理流程失败", e)
            _state.value = ProcessingState.ERROR
            
            // 返回降级结果
            ProcessResult(
                summary = originalText,
                todos = listOf(TodoItem(originalText, false)),
                rawOutput = "",
                usedFallback = true
            )
        } finally {
            _state.value = ProcessingState.IDLE
        }
    }
    
    // ========== 辅助类 ==========
    
    /**
     * 解析结果数据类
     */
    private data class ParsedResult(
        val summary: String,
        val todos: List<TodoItem>
    )
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): ProcessingState {
        return _state.value
    }
    
    /**
     * 检查是否正在处理
     */
    fun isProcessing(): Boolean {
        return _state.value != ProcessingState.IDLE && _state.value != ProcessingState.ERROR
    }
    
    /**
     * 检查CT-Transformer是否可用
     */
    fun isPunctuationRestorerAvailable(): Boolean {
        return ctTransformerAvailable
    }
}
