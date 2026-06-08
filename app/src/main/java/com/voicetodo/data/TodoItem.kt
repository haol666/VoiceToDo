package com.voicetodo.data

import com.google.gson.annotations.SerializedName

/**
 * 单个待办事项数据类
 * 用于JSON序列化和反序列化
 */
data class TodoItem(
    @SerializedName("text")
    val text: String,
    
    @SerializedName("completed")
    var completed: Boolean = false
) {
    companion object {
        /**
         * 创建空的待办事项列表
         */
    }
}
