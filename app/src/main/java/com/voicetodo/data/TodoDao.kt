package com.voicetodo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 待办事项数据访问对象
 * 提供数据库操作接口
 */
@Dao
interface TodoDao {
    
    /**
     * 插入一条待办记录
     * @return 插入记录的ID
     */
    @Insert
    suspend fun insert(record: TodoRecord): Long
    
    /**
     * 插入多条待办记录
     */
    @Insert
    suspend fun insertAll(records: List<TodoRecord>)
    
    /**
     * 更新一条待办记录
     */
    @Update
    suspend fun update(record: TodoRecord)
    
    /**
     * 删除一条待办记录
     */
    @Delete
    suspend fun delete(record: TodoRecord)
    
    /**
     * 根据ID删除记录
     */
    @Query("DELETE FROM todo_records WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * 删除所有记录
     */
    @Query("DELETE FROM todo_records")
    suspend fun deleteAll()
    
    /**
     * 根据ID查询记录
     */
    @Query("SELECT * FROM todo_records WHERE id = :id")
    suspend fun getById(id: Long): TodoRecord?
    
    /**
     * 查询所有记录，按时间戳降序排列
     * @return Flow<List<TodoRecord>> 用于响应式数据流
     */
    @Query("SELECT * FROM todo_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TodoRecord>>
    
    /**
     * 查询所有记录（一次性）
     */
    @Query("SELECT * FROM todo_records ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<TodoRecord>
    
    /**
     * 获取记录总数
     */
    @Query("SELECT COUNT(*) FROM todo_records")
    suspend fun getCount(): Int
    
    /**
     * 根据时间范围查询记录
     */
    @Query("SELECT * FROM todo_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<TodoRecord>
}
