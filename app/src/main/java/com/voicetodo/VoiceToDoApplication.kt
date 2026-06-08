package com.voicetodo

import android.app.Application
import com.voicetodo.data.AppDatabase
import com.voicetodo.data.PreferencesManager
import com.voicetodo.data.TodoRepository

/**
 * 应用程序主类
 * 管理全局单例和资源
 */
class VoiceToDoApplication : Application() {

    lateinit var database: AppDatabase
        private set
    
    lateinit var todoRepository: TodoRepository
        private set
    
    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        // 初始化数据库
        database = AppDatabase.getDatabase(this)
        
        // 初始化Repository
        todoRepository = TodoRepository(database.todoDao())
        
        // 初始化PreferencesManager
        preferencesManager = PreferencesManager(this)
    }

    companion object {
        @Volatile
        private var instance: VoiceToDoApplication? = null

        fun getInstance(): VoiceToDoApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
