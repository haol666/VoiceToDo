package com.voicetodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room数据库主类
 * 管理应用的数据持久化
 */
@Database(
    entities = [TodoRecord::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * 获取TodoDao实例
     */
    abstract fun todoDao(): TodoDao
    
    companion object {
        private const val DATABASE_NAME = "voicetodo.db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * 获取数据库实例（单例模式）
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * 数据库迁移策略（示例）
         * 当版本升级时使用
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 版本1到版本2的迁移逻辑
                // 例如：添加新列
                // database.execSQL("ALTER TABLE todo_records ADD COLUMN new_column TEXT")
            }
        }
    }
}
