package com.daykit.feature.widget

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daykit.DayKitApplication
import com.daykit.core.ui.Cyan
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.ui.MutedText
import com.daykit.core.ui.Panel
import com.daykit.core.ui.PanelAlt
import com.daykit.core.ui.SoftText
import com.daykit.core.ui.Stroke
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
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Add expense", color = SoftText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        WidgetTextField(value = name, onValueChange = { name = it }, label = "Name")
        WidgetTextField(
            value = amount,
            onValueChange = { amount = it.cleanWidgetAmountInput() },
            label = "Amount",
            keyboardType = KeyboardType.Decimal,
        )
        WidgetCategoryPicker(category = category, onCategoryChange = { category = it })
        WidgetTextField(value = note, onValueChange = { note = it.take(160) }, label = "Note")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(38.dp)) {
                Text("Cancel", color = MutedText)
            }
            Button(
                onClick = { onSave(name, amountMinor ?: 0L, category, note) },
                enabled = canSave,
                modifier = Modifier.weight(1f).height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    disabledContainerColor = PanelAlt,
                    contentColor = Color(0xFF09090B),
                    disabledContentColor = MutedText,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WidgetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodySmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Stroke,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = MutedText,
            cursorColor = Cyan,
            focusedTextColor = SoftText,
            unfocusedTextColor = SoftText,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    )
}

@Composable
private fun WidgetCategoryPicker(
    category: String,
    onCategoryChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Category", color = MutedText, style = MaterialTheme.typography.labelSmall)
                Text(category, color = SoftText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PanelAlt),
        ) {
            WIDGET_EXPENSE_CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = SoftText) },
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
