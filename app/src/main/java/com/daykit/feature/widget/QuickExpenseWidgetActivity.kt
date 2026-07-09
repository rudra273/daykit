package com.daykit.feature.widget

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daykit.DayKitApplication
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToLong

class QuickExpenseWidgetActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setFinishOnTouchOutside(true)
        window.setDimAmount(0.45f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val repository = (application as DayKitApplication).container.expenseRepository
        setContent {
            DayKitTheme {
                val scope = rememberCoroutineScope()
                QuickExpenseWidgetContent(
                    onDismiss = { finish() },
                    onSave = { name, amountMinor, category, note ->
                        scope.launch {
                            runCatching {
                                repository.addDailyExpense(
                                    expenseDate = LocalDate.now().toString(),
                                    title = name,
                                    category = category,
                                    amountMinor = amountMinor,
                                    note = note,
                                )
                            }.onSuccess {
                                Toast.makeText(this@QuickExpenseWidgetActivity, "Expense added", Toast.LENGTH_SHORT).show()
                                updateExpenseWidgets(this@QuickExpenseWidgetActivity)
                                finish()
                            }.onFailure {
                                Toast.makeText(
                                    this@QuickExpenseWidgetActivity,
                                    it.message ?: "Could not add expense",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickExpenseWidgetContent(
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(WIDGET_EXPENSE_CATEGORIES.first()) }
    var note by remember { mutableStateOf("") }
    val amountMinor = amount.toWidgetMinorOrNull()
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.card, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            "Add expense",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        AppTextField(value = name, onValueChange = { name = it }, label = "Name")
        AppTextField(
            value = amount,
            onValueChange = { amount = it.cleanWidgetAmountInput() },
            label = "Amount",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        WidgetCategoryPicker(category = category, onCategoryChange = { category = it })
        AppTextField(value = note, onValueChange = { note = it.take(160) }, label = "Note")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            AppTextButton(text = "Cancel", modifier = Modifier.weight(1f), color = MaterialTheme.extendedColors.textMuted, onClick = onDismiss)
            PrimaryButton(
                text = "Save",
                enabled = canSave,
                modifier = Modifier.weight(1f),
                onClick = { onSave(name, amountMinor ?: 0L, category, note) },
            )
        }
    }
}

@Composable
private fun WidgetCategoryPicker(
    category: String,
    onCategoryChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.extendedColors.inputField, MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text("Category", color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(category, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.extendedColors.card),
        ) {
            WIDGET_EXPENSE_CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onCategoryChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun String.cleanWidgetAmountInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered.take(9)
    } else {
        filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).filter(Char::isDigit).take(2)
    }
}

private fun String.toWidgetMinorOrNull(): Long? {
    val value = toDoubleOrNull() ?: return null
    return (value * 100.0).roundToLong()
}

private val WIDGET_EXPENSE_CATEGORIES = listOf(
    "General",
    "Food",
    "Travel",
    "Shopping",
    "Bills",
    "Health",
    "Work",
    "Other",
)
