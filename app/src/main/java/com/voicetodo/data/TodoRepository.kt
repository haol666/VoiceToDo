package com.voicetodo.data

import kotlinx.coroutines.flow.Flow

/**
 * 待办事项数据仓库
 * 统一管理数据访问逻辑
 */
class TodoRepository(private val todoDao: TodoDao) {
    
    /**
     * 插入一条待办记录
     */
    suspend fun insert(record: TodoRecord): Long {
        return todoDao.insert(record)
    }
    
    /**
     * 插入多条待办记录
     */
    suspend fun insertAll(records: List<TodoRecord>) {
        todoDao.insertAll(records)
    }
    
    /**
     * 更新一条待办记录
     */
    suspend fun update(record: TodoRecord) {
        todoDao.update(record)
    }
    
    /**
     * 删除一条待办记录
     */
    suspend fun delete(record: TodoRecord) {
        todoDao.delete(record)
    }
    
    /**
     * 根据ID删除记录
     */
    suspend fun deleteById(id: Long) {
        todoDao.deleteById(id)
    }
    
    /**
     * 删除所有记录
     */
    suspend fun deleteAll() {
        todoDao.deleteAll()
    }
    
    /**
     * 根据ID查询记录
     */
    suspend fun getById(id: Long): TodoRecord? {
        return todoDao.getById(id)
    }
    
    /**
     * 获取所有待办记录（响应式数据流）
     */
    fun getAll(): Flow<List<TodoRecord>> {
        return todoDao.getAll()
    }
    
    /**
     * 获取所有待办记录（一次性）
     */
    suspend fun getAllOnce(): List<TodoRecord> {
        return todoDao.getAllOnce()
    }
    
    /**
     * 获取记录总数
     */
    suspend fun getCount(): Int {
        return todoDao.getCount()
    }
    
    /**
     * 根据时间范围查询记录
     */
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<TodoRecord> {
        return todoDao.getByTimeRange(startTime, endTime)
    }
}
