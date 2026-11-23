package com.example.stride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun AudioCueDialog(
    enabled: Boolean,
    status: String,
    canPreview: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPreview: (CueType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio cues") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound and voice", Modifier.weight(1f))
                    Switch(enabled, onEnabled, modifier = Modifier.semantics { contentDescription = "Enable audio cues" })
                }
                Text("Hear the interval name and a distinct tone when each interval or repetition starts. A final cue marks completion.")
                Text(status, style = MaterialTheme.typography.bodySmall)
                Text("Uses media volume and your connected headphones or speaker. Keep the app open during your run; the screen stays awake while the timer runs.", style = MaterialTheme.typography.bodySmall)
                if (!canPreview) Text("Preview sounds before or after your run.")
                CueType.entries.forEach { cue ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cue.label)
                            Text(cue.description, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(enabled = enabled && canPreview, onClick = { onPreview(cue) }) {
                            Text("Play", modifier = Modifier.semantics { contentDescription = "Preview ${cue.label}" })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
