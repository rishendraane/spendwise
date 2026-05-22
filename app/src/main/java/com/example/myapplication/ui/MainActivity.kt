package com.example.myapplication.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.data.Expense
import com.example.myapplication.databinding.DialogAddExpenseBinding
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this).get(ExpenseViewModel::class.java)

        adapter = ExpenseAdapter { expense -> viewModel.delete(expense) }
        binding.recyclerExpenses.layoutManager = LinearLayoutManager(this)
        binding.recyclerExpenses.adapter = adapter

        binding.fabAddExpense.setOnClickListener { showAddExpenseDialog() }

        viewModel.allExpenses.observe(this) { expenses ->
            adapter.submitList(expenses)
            updateSummary(expenses)
            binding.emptyView.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateSummary(expenses: List<Expense>) {
        val total = expenses.sumOf { it.amount }
        binding.tvTotalSpent.text = getString(R.string.total_spent_format, formatCurrency(total))
        binding.tvExpenseCount.text = getString(R.string.expense_count_format, expenses.size)
    }

    private fun showAddExpenseDialog() {
        val dialogBinding = DialogAddExpenseBinding.inflate(LayoutInflater.from(this))
        val categories = resources.getStringArray(R.array.expense_categories)
        dialogBinding.autoCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))

        AlertDialog.Builder(this)
            .setTitle(R.string.add_expense)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.inputTitle.text.toString().trim()
                val amountText = dialogBinding.inputAmount.text.toString().trim()
                val category = dialogBinding.autoCategory.text.toString().trim().ifEmpty { categories.first() }
                val description = dialogBinding.inputDescription.text.toString().trim()

                val amount = amountText.toDoubleOrNull()
                if (title.isEmpty() || amount == null || amount <= 0.0) {
                    Toast.makeText(this, R.string.invalid_input_message, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.insert(title, amount, category, System.currentTimeMillis(), description)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatCurrency(value: Double): String {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(value)
    }
}
