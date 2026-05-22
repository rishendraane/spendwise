package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.Expense
import com.example.myapplication.databinding.ItemTransactionBinding
import java.text.NumberFormat
import java.util.*

import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import java.text.SimpleDateFormat

class ExpenseAdapter(
    private val onDelete: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExpenseViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: Expense) {
            val context = binding.root.context
            binding.tvTitle.text = expense.title
            binding.tvCategory.text = expense.category
            binding.tvAmount.text = formatCurrency(expense.amount)
            
            if (expense.description.trim().isNotEmpty()) {
                binding.tvDescription.text = expense.description
                binding.tvDescription.visibility = android.view.View.VISIBLE
            } else {
                binding.tvDescription.visibility = android.view.View.GONE
            }

            // Format transaction date
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.tvDate.text = sdf.format(Date(expense.date))

            // Map category to color resource
            val categoryColorRes = when (expense.category.trim().lowercase()) {
                "food" -> R.color.category_food
                "transport" -> R.color.category_transport
                "shopping" -> R.color.category_shopping
                "utilities" -> R.color.category_utilities
                "health" -> R.color.category_health
                "entertainment" -> R.color.category_entertainment
                else -> R.color.category_other
            }
            val colorVal = ContextCompat.getColor(context, categoryColorRes)

            // Left vertical indicator
            binding.vCategoryIndicator.setBackgroundColor(colorVal)

            // Category badge pill background
            val badge = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(colorVal)
            }
            binding.tvCategory.background = badge

            binding.btnDelete.setOnClickListener { onDelete(expense) }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
    }
}

private class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
    override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean = oldItem == newItem
}
