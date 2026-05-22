package com.example.myapplication.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.Expense
import com.example.myapplication.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(AppDatabase.getDatabase(application).expenseDao())
    val allExpenses = repository.allExpenses().asLiveData()

    private val sharedPrefs = application.getSharedPreferences("SpendWisePrefs", Context.MODE_PRIVATE)

    // User's configured monthly salary amount (default: ₹30,000)
    private val _salary = MutableLiveData<Double>().apply {
        value = sharedPrefs.getFloat("salary_amount", 30000f).toDouble()
    }
    val salary: LiveData<Double> get() = _salary

    // User's configured next salary date (timestamp in milliseconds)
    private val _nextSalaryDate = MutableLiveData<Long>().apply {
        val defaultDate = calculateDefaultNextSalaryDate()
        value = sharedPrefs.getLong("next_salary_date", defaultDate)
    }
    val nextSalaryDate: LiveData<Long> get() = _nextSalaryDate

    // Total expenses recorded inside the current active salary cycle
    val totalSpentInCycle = MediatorLiveData<Double>().apply {
        fun update() {
            val expenses = allExpenses.value ?: emptyList()
            val nextDate = _nextSalaryDate.value ?: return
            val startDate = getStartOfSalaryCycle(nextDate)

            // Sum up expenses whose dates lie between [startDate, nextDate]
            val sum = expenses.filter { it.date in startDate..nextDate }
                .sumOf { it.amount }
            value = sum
        }
        addSource(allExpenses) { update() }
        addSource(_nextSalaryDate) { update() }
    }

    // Remaining salary (Salary - Total spent in current cycle)
    val remainingSalary = MediatorLiveData<Double>().apply {
        fun update() {
            val sal = _salary.value ?: 0.0
            val spent = totalSpentInCycle.value ?: 0.0
            value = sal - spent
        }
        addSource(_salary) { update() }
        addSource(totalSpentInCycle) { update() }
    }

    // Days remaining until the next salary day
    val daysRemaining = MediatorLiveData<Long>().apply {
        fun update() {
            val nextDate = _nextSalaryDate.value ?: return
            val now = System.currentTimeMillis()
            val diffMs = nextDate - now
            val days = (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
            value = days
        }
        addSource(_nextSalaryDate) { update() }
    }

    // Suggested amount to spend per day (Remaining Salary / Days Remaining)
    val suggestedDailySpend = MediatorLiveData<Double>().apply {
        fun update() {
            val remaining = remainingSalary.value ?: 0.0
            val daysRem = daysRemaining.value ?: 30L
            val dailyLimit = if (daysRem > 0) {
                (remaining / daysRem).coerceAtLeast(0.0)
            } else {
                remaining.coerceAtLeast(0.0)
            }
            value = dailyLimit
        }
        addSource(remainingSalary) { update() }
        addSource(daysRemaining) { update() }
    }

    // Spending score calculation
    val habitScore = MediatorLiveData<Double>().apply {
        fun update() {
            val expenses = allExpenses.value ?: emptyList()
            val salLimit = salary.value ?: 30000.0
            val spent = totalSpentInCycle.value ?: 0.0
            val daysRem = daysRemaining.value ?: 30L
            val nextDate = nextSalaryDate.value ?: System.currentTimeMillis()
            
            if (expenses.isEmpty() || spent <= 0) {
                value = 10.0
                return
            }

            var score = 10.0

            // 1. Pacing penalty
            val cycleStart = getStartOfSalaryCycle(nextDate)
            val totalCycleDays = ((nextDate - cycleStart) / (1000 * 60 * 60 * 24)).coerceAtLeast(1).toDouble()
            val elapsedDays = (totalCycleDays - daysRem).coerceAtLeast(0.0)
            val cycleProgress = if (totalCycleDays > 0) elapsedDays / totalCycleDays else 0.0
            val budgetProgress = if (salLimit > 0) spent / salLimit else 0.0

            if (budgetProgress > cycleProgress + 0.10) {
                val excess = budgetProgress - cycleProgress - 0.10
                score -= (excess * 6.0).coerceAtMost(4.0) // max 4 points off for pacing
            }
            if (spent > salLimit) {
                score -= 3.0 // penalty for overdrawing budget
            }

            // 2. Category-wise checks
            val cycleExpenses = expenses.filter { it.date in cycleStart..nextDate }
            val foodSpent = cycleExpenses.filter { it.category.lowercase() == "food" }.sumOf { it.amount }
            val shoppingSpent = cycleExpenses.filter { it.category.lowercase() == "shopping" }.sumOf { it.amount }
            val entertainmentSpent = cycleExpenses.filter { it.category.lowercase() == "entertainment" }.sumOf { it.amount }

            // Food limit: 20% of salary
            if (foodSpent > 0.20 * salLimit) {
                val excess = foodSpent - (0.20 * salLimit)
                score -= (excess / (0.10 * salLimit) * 1.5).coerceAtMost(2.0)
            }
            // Shopping limit: 15% of salary
            if (shoppingSpent > 0.15 * salLimit) {
                val excess = shoppingSpent - (0.15 * salLimit)
                score -= (excess / (0.05 * salLimit) * 2.0).coerceAtMost(3.0)
            }
            // Entertainment limit: 10% of salary
            if (entertainmentSpent > 0.10 * salLimit) {
                val excess = entertainmentSpent - (0.10 * salLimit)
                score -= (excess / (0.05 * salLimit) * 2.5).coerceAtMost(3.0)
            }

            // Coerce score between 1.0 and 10.0
            val finalScore = String.format(Locale.US, "%.1f", score.coerceIn(1.0, 10.0)).toDouble()
            value = finalScore
        }
        addSource(allExpenses) { update() }
        addSource(salary) { update() }
        addSource(totalSpentInCycle) { update() }
        addSource(daysRemaining) { update() }
    }

    val habitAdvice = MediatorLiveData<String>().apply {
        fun update() {
            val scoreVal = habitScore.value ?: 10.0
            val expenses = allExpenses.value ?: emptyList()
            val salLimit = salary.value ?: 30000.0
            val spent = totalSpentInCycle.value ?: 0.0
            val remainingVal = remainingSalary.value ?: 0.0
            val daysRem = daysRemaining.value ?: 30L
            val nextDate = nextSalaryDate.value ?: System.currentTimeMillis()
            val cycleStart = getStartOfSalaryCycle(nextDate)
            
            if (expenses.isEmpty()) {
                val dailyBudgetVal2 = suggestedDailySpend.value ?: 0.0
                val dailyBudgetStr2 = String.format(Locale.US, "%.0f", dailyBudgetVal2)
                value = "\u2022 Daily Budget: Try to stay under \u20B9$dailyBudgetStr2 per day.\n\n\u2022 No transactions yet. Add some to start tracking your spending habits! \uD83D\uDCB8"
                return
            }

            val cycleExpenses = expenses.filter { it.date in cycleStart..nextDate }
            val foodSpent = cycleExpenses.filter { it.category.lowercase() == "food" }.sumOf { it.amount }
            val shoppingSpent = cycleExpenses.filter { it.category.lowercase() == "shopping" }.sumOf { it.amount }
            val entertainmentSpent = cycleExpenses.filter { it.category.lowercase() == "entertainment" }.sumOf { it.amount }

            val totalCycleDays = ((nextDate - cycleStart) / (1000 * 60 * 60 * 24)).coerceAtLeast(1).toDouble()
            val elapsedDays = (totalCycleDays - daysRem).coerceAtLeast(0.0)

            val dailyBudgetVal = if (daysRem > 0) (remainingVal / daysRem).coerceAtLeast(0.0) else remainingVal.coerceAtLeast(0.0)
            val dailyBudgetStr = String.format(Locale.US, "%.0f", dailyBudgetVal)

            val tips = mutableListOf<String>()
            tips.add("Daily Budget: Try to stay under ₹$dailyBudgetStr per day to extend your balance.")

            if (foodSpent > 0.20 * salLimit) {
                tips.add("Food spending is ₹${String.format(Locale.US, "%.0f", foodSpent)} (${String.format(Locale.US, "%.0f", foodSpent / salLimit * 100)}% of limit). Try cooking at home to save.")
            }
            if (shoppingSpent > 0.15 * salLimit) {
                tips.add("Shopping is high at ₹${String.format(Locale.US, "%.0f", shoppingSpent)} (${String.format(Locale.US, "%.0f", shoppingSpent / salLimit * 100)}% of limit). Postpone non-essential shopping.")
            }
            if (entertainmentSpent > 0.10 * salLimit) {
                tips.add("Entertainment is ₹${String.format(Locale.US, "%.0f", entertainmentSpent)} (${String.format(Locale.US, "%.0f", entertainmentSpent / salLimit * 100)}% of limit). Look for free recreation.")
            }

            if (spent > salLimit) {
                tips.add("Critical: Budget is fully depleted! Stop all non-essential expenses.")
            } else if (salLimit > 0 && spent / salLimit > elapsedDays / totalCycleDays + 0.10) {
                tips.add("Pacing: You are spending faster than days are passing. Slow down.")
            }

            if (tips.size <= 1) {
                tips.add("Pacing looks great! Keep tracking daily expenses to hit your goals.")
            }

            value = tips.joinToString(separator = "\n\n") { "• $it" }
        }
        addSource(habitScore) { update() }
        addSource(allExpenses) { update() }
        addSource(salary) { update() }
        addSource(remainingSalary) { update() }
        addSource(daysRemaining) { update() }
    }

    val aiCoachInsight = MutableLiveData<String>().apply {
        value = "Get a personalized review of your transactions from the NVIDIA AI Coach."
    }
    val aiCoachScore = MutableLiveData<Double?>()
    val aiCoachLoading = MutableLiveData<Boolean>().apply { value = false }

    fun fetchAiCoachReview() {
        val expenses = allExpenses.value ?: emptyList()
        val salLimit = salary.value ?: 30000.0
        val spent = totalSpentInCycle.value ?: 0.0
        val remaining = remainingSalary.value ?: 30000.0
        val daysRem = daysRemaining.value ?: 30L
        val nextDate = nextSalaryDate.value ?: System.currentTimeMillis()
        val cycleStart = getStartOfSalaryCycle(nextDate)
        
        val cycleExpenses = expenses.filter { it.date in cycleStart..nextDate }
        
        aiCoachLoading.postValue(true)
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Formulate prompt
                val txString = cycleExpenses.joinToString(separator = "\n") { 
                    "- ${it.title} (Category: ${it.category}, Amount: ₹${it.amount})"
                }
                
                val userPrompt = """
                    Monthly Salary: ₹$salLimit
                    Total Spent: ₹$spent
                    Remaining: ₹$remaining
                    Days Remaining in Cycle: $daysRem days
                    Transactions:
                    $txString
                """.trimIndent()

                val url = java.net.URL("https://integrate.api.nvidia.com/v1/chat/completions")
                val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer MmRvYmVtNGZudmNucnU0ZGxmcDhicXF2dGo6ZWViZGI1YjQtMGQ1MS00NTU3LWExNWYtODExNzZlYjc2ZGYx")
                }

                val requestBody = org.json.JSONObject().apply {
                    put("model", "meta/llama-3.1-8b-instruct")
                    val messages = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a professional financial planner and spending habits coach. Analyze the user's monthly salary, remaining balance, and list of transactions. Give them a numeric rating out of 10 and exactly 2 sentences of highly actionable, constructive feedback or encouragement. Keep the tone friendly and encouraging (use emojis!). Format your response in plain text as a JSON object: {\"score\": 8.5, \"insight\": \"...\"}")
                        })
                        put(org.json.JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    }
                    put("messages", messages)
                    put("temperature", 0.3)
                    put("max_tokens", 256)
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = org.json.JSONObject(response)
                    val rawContent = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    var cleanedContent = rawContent.trim()
                    if (cleanedContent.startsWith("```")) {
                        cleanedContent = cleanedContent.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    }
                    
                    val innerJson = org.json.JSONObject(cleanedContent)
                    val score = innerJson.getDouble("score")
                    val insight = innerJson.getString("insight")

                    aiCoachScore.postValue(score)
                    aiCoachInsight.postValue(insight)
                } else {
                    val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    aiCoachInsight.postValue("AI Coach is currently offline. Error code: $responseCode. Please try again later.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                aiCoachInsight.postValue("Could not reach AI Coach. Please check your internet connection and try again.")
            } finally {
                aiCoachLoading.postValue(false)
            }
        }
    }

    fun preloadSampleData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            val samples = listOf(
                Expense(title = "Fresh Veggies & Fruits", amount = 850.0, category = "Food", description = "Weekly grocery restock", date = now - 5 * oneDayMs),
                Expense(title = "Office Metro Ride", amount = 120.0, category = "Transport", description = "Commute to work", date = now - 4 * oneDayMs),
                Expense(title = "Nike Training Shoes", amount = 4200.0, category = "Shopping", description = "New running gear", date = now - 3 * oneDayMs),
                Expense(title = "Water & Broadband Bill", amount = 1850.0, category = "Utilities", description = "Monthly house bills", date = now - 2 * oneDayMs),
                Expense(title = "Marvel Movie Tickets", amount = 650.0, category = "Entertainment", description = "Weekend cinema with friend", date = now - 1 * oneDayMs),
                Expense(title = "Dental Health Checkup", amount = 1200.0, category = "Health", description = "Routine teeth scaling", date = now),
                Expense(title = "Starbucks Cappuccino", amount = 350.0, category = "Food", description = "Afternoon coffee break", date = now)
            )
            for (sample in samples) {
                repository.insertExpense(sample)
            }
        }
    }

    init {
        // Ensure the salary date is always in the future (roll forward if needed)
        checkAndRollForwardSalaryDate()
    }

    fun insert(title: String, amount: Double, category: String, date: Long, description: String) {
        viewModelScope.launch {
            repository.insertExpense(Expense(title = title, amount = amount, category = category, description = description, date = date))
        }
    }

    fun delete(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // Update salary limit and persist to preferences
    fun updateSalary(newSalary: Double) {
        _salary.value = newSalary
        sharedPrefs.edit().putFloat("salary_amount", newSalary.toFloat()).apply()
    }

    // Update next salary date and persist to preferences
    fun updateNextSalaryDate(timestamp: Long) {
        _nextSalaryDate.value = timestamp
        sharedPrefs.edit().putLong("next_salary_date", timestamp).apply()
        // Force refresh calculations by calling checkAndRollForwardSalaryDate
        checkAndRollForwardSalaryDate()
    }

    // Computes the start of the current cycle by stepping back 1 month at a time
    // until the start date is at or before today. This ensures today always falls
    // within the [cycleStart, nextSalaryDate] range.
    fun getStartOfSalaryCycle(nextSalaryDateMs: Long): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = nextSalaryDateMs
        cal.add(Calendar.MONTH, -1)
        // Keep stepping back if the start date is still in the future
        while (cal.timeInMillis > now) {
            cal.add(Calendar.MONTH, -1)
        }
        return cal.timeInMillis
    }

    // Default next salary date is the 1st of the next calendar month at midnight
    private fun calculateDefaultNextSalaryDate(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // If current time is greater than next salary date, advance it monthly until it's in the future
    fun checkAndRollForwardSalaryDate() {
        val now = System.currentTimeMillis()
        val currentDate = _nextSalaryDate.value ?: return
        if (now >= currentDate) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = currentDate
            while (cal.timeInMillis <= now) {
                cal.add(Calendar.MONTH, 1)
            }
            val rolledDate = cal.timeInMillis
            _nextSalaryDate.value = rolledDate
            sharedPrefs.edit().putLong("next_salary_date", rolledDate).apply()
        }
    }
}
