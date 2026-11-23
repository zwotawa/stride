package com.example.stride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun GroupDialog(
    intervals: List<Interval>,
    groupId: String?,
    onDismiss: () -> Unit,
    onSave: (Set<Int>, String, Int) -> Unit
) {
    val original = intervals.firstOrNull { groupId != null && it.groupId == groupId }
    var title by remember { mutableStateOf(original?.groupName ?: "Run + recovery") }
    var repeats by remember { mutableStateOf((original?.groupRepetitions ?: 2).toString()) }
    var selected by remember { mutableStateOf(intervals.indices.filter { groupId != null && intervals[it].groupId == groupId }.toSet()) }
    val count = repeats.toIntOrNull()
    val consecutive = selected.isNotEmpty() && selected.size == selected.max() - selected.min() + 1
    val valid = consecutive && count != null && count in 1..99 && title.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (groupId == null) "Group intervals" else "Edit group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repeats, { if (it.length <= 2 && it.all(Char::isDigit)) repeats = it },
                    label = { Text("Group times to perform") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = count == null || count !in 1..99,
                    supportingText = { Text("1–99 rounds. Finish the selected intervals in order, then repeat the whole group.") },
                    modifier = Modifier.fillMaxWidth())
                Text("Select consecutive intervals. Intervals in another group are unavailable.")
                intervals.forEachIndexed { i, interval ->
                    val available = interval.groupId == null || interval.groupId == groupId
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = i in selected, enabled = available, role = Role.Checkbox,
                            onValueChange = { checked -> selected = if (checked) selected + i else selected - i }
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = i in selected, onCheckedChange = null, enabled = available)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Select interval ${i + 1}: ${interval.kind}")
                            Text(if (available) "%02d:%02d · %d times".format(interval.seconds / 60, interval.seconds % 60, interval.repetitions) else "In ${interval.groupName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!consecutive) Text("Choose an unbroken sequence of intervals.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(selected, title, count!!) }) { Text("Save group") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
