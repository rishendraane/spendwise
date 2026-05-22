package com.example.myapplication.ui

import android.app.DatePickerDialog
import android.content.res.ColorStateList
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
import com.example.myapplication.databinding.DialogSalarySettingsBinding
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.viewmodel.ExpenseViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        binding.btnEditSalary.setOnClickListener { showSalarySettingsDialog() }
        binding.btnGetAiReview.setOnClickListener { viewModel.fetchAiCoachReview() }

        // Load sample transactions
        binding.btnLoadSampleData.setOnClickListener {
            viewModel.preloadSampleData()
            Toast.makeText(this, "Sample transactions loaded!", Toast.LENGTH_SHORT).show()
        }

        // Core Expenses Observer
        viewModel.allExpenses.observe(this) { expenses ->
            adapter.submitList(expenses)
            binding.emptyView.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
        }

        // Spend Score & Habit Observers
        viewModel.habitScore.observe(this) { score ->
            binding.tvScore.text = String.format(Locale.US, "%.1f", score)
            binding.cpScoreProgress.progress = (score * 10).toInt()

            // Update gauge color based on score
            val colorRes = when {
                score >= 8.0 -> R.color.emerald_green
                score >= 5.0 -> R.color.warning_orange
                else -> R.color.danger_red
            }
            binding.cpScoreProgress.setIndicatorColor(resources.getColor(colorRes, null))

            // Mood rating text
            val moodText = when {
                score >= 8.0 -> "😄 Smart Saver"
                score >= 5.0 -> "😐 Average Spender"
                else -> "😭 Broke Legend"
            }
            binding.tvScoreDescription.text = moodText
        }

        viewModel.habitAdvice.observe(this) { advice ->
            binding.tvScoreAdvice.text = advice
        }

        // AI Coach Observers
        viewModel.aiCoachLoading.observe(this) { loading ->
            if (loading) {
                binding.pbAiCoachProgress.visibility = View.VISIBLE
                binding.btnGetAiReview.visibility = View.INVISIBLE
            } else {
                binding.pbAiCoachProgress.visibility = View.GONE
                binding.btnGetAiReview.visibility = View.VISIBLE
            }
        }

        viewModel.aiCoachInsight.observe(this) { insight ->
            updateAiCoachText()
        }

        viewModel.aiCoachScore.observe(this) { score ->
            updateAiCoachText()
        }

        // Reactive Observers for Salary and Time Tracking
        viewModel.salary.observe(this) {
            updateProgressAndPace()
        }
        viewModel.totalSpentInCycle.observe(this) {
            updateProgressAndPace()
        }
        viewModel.remainingSalary.observe(this) {
            updateProgressAndPace()
        }
        viewModel.nextSalaryDate.observe(this) {
            updateProgressAndPace()
        }
        viewModel.daysRemaining.observe(this) {
            updateProgressAndPace()
        }
        viewModel.suggestedDailySpend.observe(this) { daily ->
            binding.tvSuggestedDailySpend.text = "Daily limit: ${formatCurrency(daily)}"
        }
    }

    private fun updateAiCoachText() {
        val scoreVal = viewModel.aiCoachScore.value
        val insightVal = viewModel.aiCoachInsight.value
        if (scoreVal != null && insightVal != null) {
            binding.tvAiCoachInsight.text = "AI Score: $scoreVal/10\n\n$insightVal"
        } else if (insightVal != null) {
            binding.tvAiCoachInsight.text = insightVal
        }
    }

    private fun updateProgressAndPace() {
        val salaryVal = viewModel.salary.value ?: 30000.0
        val spentVal = viewModel.totalSpentInCycle.value ?: 0.0
        val remainingVal = viewModel.remainingSalary.value ?: 30000.0
        val daysRemainingVal = viewModel.daysRemaining.value ?: 30L
        val nextSalaryMs = viewModel.nextSalaryDate.value ?: System.currentTimeMillis()

        // 1. Update text displays
        binding.tvSalaryLimit.text = formatCurrency(salaryVal)
        binding.tvTotalSpent.text = formatCurrency(spentVal)
        binding.tvRemainingSalary.text = formatCurrency(remainingVal)

        // Adjust remaining salary text color if it's overdrawn
        if (remainingVal < 0) {
            binding.tvRemainingSalary.setTextColor(resources.getColor(R.color.danger_red, null))
        } else {
            binding.tvRemainingSalary.setTextColor(resources.getColor(R.color.white, null))
        }

        // 2. Calculate progress bar percentage
        val progressPercent = if (salaryVal > 0) {
            ((spentVal / salaryVal) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        binding.pbSalaryProgress.progress = progressPercent

        // Change progress bar tint based on the remaining budget percentage
        val remainingPercent = 100 - progressPercent
        val progressColor = when {
            remainingPercent > 50 -> resources.getColor(R.color.emerald_green, null)
            remainingPercent > 20 -> resources.getColor(R.color.warning_orange, null)
            else -> resources.getColor(R.color.danger_red, null)
        }
        binding.pbSalaryProgress.progressTintList = ColorStateList.valueOf(progressColor)

        // 3. Format next salary countdown text
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(nextSalaryMs))
        binding.tvSalaryCountdown.text = if (daysRemainingVal <= 0L) {
            "Salary day is today! 🎉 ($formattedDate)"
        } else {
            "Next salary in $daysRemainingVal day${if (daysRemainingVal > 1) "s" else ""} ($formattedDate)"
        }

        // 4. Calculate Pace Status
        // Cycle starts 1 month before nextSalaryMs
        val cycleStartDate = viewModel.getStartOfSalaryCycle(nextSalaryMs)
        val totalCycleMs = nextSalaryMs - cycleStartDate
        val totalCycleDays = (totalCycleMs / (1000 * 60 * 60 * 24)).coerceAtLeast(1).toDouble()
        val elapsedDays = (totalCycleDays - daysRemainingVal).coerceAtLeast(0.0)

        val timeElapsedPercent = elapsedDays / totalCycleDays
        val budgetSpentPercent = if (salaryVal > 0) spentVal / salaryVal else 0.0

        val paceStatus: String
        val badgeColor: Int

        when {
            remainingVal < 0 -> {
                paceStatus = "OVERDRAWN"
                badgeColor = resources.getColor(R.color.danger_red, null)
            }
            budgetSpentPercent > timeElapsedPercent + 0.10 -> { // Spent 10% more than cycle time elapsed
                paceStatus = "SPENDING FAST"
                badgeColor = resources.getColor(R.color.warning_orange, null)
            }
            else -> {
                paceStatus = "ON TRACK"
                badgeColor = resources.getColor(R.color.emerald_green, null)
            }
        }

        binding.tvPaceStatus.text = paceStatus
        binding.cardPaceStatus.setCardBackgroundColor(badgeColor)
    }

    private fun showSalarySettingsDialog() {
        val dialogBinding = DialogSalarySettingsBinding.inflate(LayoutInflater.from(this))
        val currentSalary = viewModel.salary.value ?: 30000.0
        dialogBinding.etSalaryAmount.setText(String.format(Locale.US, "%.0f", currentSalary))

        var selectedDateMs = viewModel.nextSalaryDate.value ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dialogBinding.btnPickSalaryDate.text = dateFormat.format(Date(selectedDateMs))

        dialogBinding.btnPickSalaryDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = selectedDateMs
            }
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    selectedDateMs = selectedCal.timeInMillis
                    dialogBinding.btnPickSalaryDate.text = dateFormat.format(selectedCal.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Salary Settings")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newSalary = dialogBinding.etSalaryAmount.text.toString().toDoubleOrNull() ?: currentSalary
                viewModel.updateSalary(newSalary)
                viewModel.updateNextSalaryDate(selectedDateMs)
                Toast.makeText(this, "Salary settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddExpenseDialog() {
        val dialogBinding = DialogAddExpenseBinding.inflate(LayoutInflater.from(this))
        val categories = resources.getStringArray(R.array.expense_categories)
        dialogBinding.autoCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))

        MaterialAlertDialogBuilder(this)
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
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return format.format(value)
    }
}
