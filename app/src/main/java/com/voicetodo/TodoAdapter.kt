package com.voicetodo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.voicetodo.data.TodoItem

/**
 * 待待事项列表适配器
 */
class TodoAdapter(
    private val todoList: MutableList<TodoItem>
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {
    
    /**
     * 待办事项ViewHolder
     */
    class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbCompleted: CheckBox = itemView.findViewById(R.id.cbCompleted)
        val tvTodoText: TextView = itemView.findViewById(R.id.tvTodoText)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todoList[position]
        
        holder.tvTodoText.text = todo.text
        holder.cbCompleted.isChecked = todo.completed
        
        holder.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
            todo.completed = isChecked
        }
    }
    
    override fun getItemCount(): Int {
        return todoList.size
    }
    
    fun updateTodos(newTodos: List<TodoItem>) {
        todoList.clear()
        todoList.addAll(newTodos)
        notifyDataSetChanged()
    }
}
