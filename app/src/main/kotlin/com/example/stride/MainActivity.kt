package com.example.stride

import android.app.Application
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Interval(
    val kind: String,
    val seconds: Int,
    val repetitions: Int = 1,
    val groupId: String? = null,
    val groupName: String = "Interval group",
    val groupRepetitions: Int = 1,
    val groupRound: Int = 1
)
private fun List<Interval>.blocks(): List<List<Interval>> {
    val result = mutableListOf<MutableList<Interval>>()
    for (interval in this) {
        if (interval.groupId != null && result.lastOrNull()?.last()?.groupId == interval.groupId)
            result.last().add(interval)
        else result.add(mutableListOf(interval))
    }
    return result
}
private fun List<Interval>.totalSeconds(): Long = blocks().sumOf { block ->
    block.sumOf { it.seconds.toLong() * it.repetitions } * block.first().groupRepetitions
}
private val kinds = listOf("Warm-up", "Run", "Jog", "Recovery", "Cool-down")
private val ink = Color(0xFF142D29)
private val green = Color(0xFF216552)
private fun clock(seconds: Int) = clock(seconds.toLong())
private fun clock(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun tint(kind: String) = when (kind) {
    "Run" -> Color(0xFFDCEAA6)
    "Jog" -> Color(0xFFF7D8BC)
    "Recovery" -> Color(0xFFD7E8FA)
    else -> Color(0xFFF1E5CD)
}

class RunModel @JvmOverloads constructor(application: Application, private val cuePlayer: CuePlayer = AndroidCuePlayer(application)) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("plan", 0)
    var audioEnabled by mutableStateOf(prefs.getBoolean("audioEnabled", true))
        private set
    val audioStatus get() = cuePlayer.status
    var name by mutableStateOf(prefs.getString("name", "My interval run") ?: "My interval run")
    var intervals by mutableStateOf(load())
        private set
    var session by mutableStateOf<List<Interval>?>(null)
        private set
    var index by mutableIntStateOf(0)
        private set
    var repetition by mutableIntStateOf(1)
        private set
    var remaining by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(false)
        private set
    private var deadline = 0L
    val complete get() = session?.let { index >= it.size } == true

    init {
        viewModelScope.launch {
            while (true) {
                delay(100)
                val steps = session ?: continue
                if (paused || complete) continue
                val now = SystemClock.elapsedRealtime()
                val previousIndex = index
                val previousRepetition = repetition
                while (index < steps.size && now >= deadline) {
                    if (repetition < steps[index].repetitions) repetition++
                    else { index++; repetition = 1 }
                    if (index < steps.size) deadline += steps[index].seconds * 1000L
                }
                remaining = if (complete) 0 else ((deadline - now + 999) / 1000).toInt()
                if (index != previousIndex || repetition != previousRepetition) announceCurrent()
            }
        }
    }
    fun changeAudioEnabled(enabled: Boolean) {
        audioEnabled = enabled
        prefs.edit().putBoolean("audioEnabled", enabled).apply()
        if (!enabled) cuePlayer.stop()
    }
    fun previewCue(type: CueType) {
        if (audioEnabled && session == null) cuePlayer.play(AudioCue(type))
    }
    fun stopPreview() {
        if (session == null) cuePlayer.stop()
    }
    private fun announceCurrent() {
        if (!audioEnabled || paused) return
        val steps = session ?: return
        val current = steps.getOrNull(index)
        val cue = if (current == null) AudioCue(CueType.COMPLETE, "Run complete. Well done.")
        else AudioCue(CueType.forInterval(current.kind), buildString {
            append(current.kind).append(".")
            if (current.groupId != null) append(" Round ").append(current.groupRound).append(" of ").append(current.groupRepetitions).append(".")
            if (current.repetitions > 1) append(" Repetition ").append(repetition).append(" of ").append(current.repetitions).append(".")
        })
        cuePlayer.play(cue)
    }
    override fun onCleared() {
        cuePlayer.release()
        super.onCleared()
    }
    private fun load(): List<Interval> = runCatching {
        val array = JSONArray(prefs.getString("intervals", null) ?: error("No plan"))
        List(array.length()) {
            val item = array.getJSONObject(it)
            Interval(
                item.getString("kind"), item.getInt("seconds").coerceIn(1, 5999),
                item.optInt("repetitions", 1).coerceIn(1, 99),
                item.optString("groupId").takeIf { id -> id.isNotBlank() },
                item.optString("groupName", "Interval group"),
                if (item.optString("groupId").isNotBlank()) item.optInt("groupRepetitions", 1).coerceIn(1, 99) else 1
            )
        }.take(100)
    }.getOrElse { listOf(Interval("Warm-up", 300), Interval("Run", 180), Interval("Recovery", 90), Interval("Run", 180), Interval("Cool-down", 300)) }
    fun save() {
        val array = JSONArray()
        intervals.forEach {
            val item = JSONObject().put("kind", it.kind).put("seconds", it.seconds).put("repetitions", it.repetitions)
            if (it.groupId != null) item.put("groupId", it.groupId).put("groupName", it.groupName).put("groupRepetitions", it.groupRepetitions)
            array.put(item)
        }
        prefs.edit().putString("name", name).putString("intervals", array.toString()).apply()
    }
    fun update(position: Int?, interval: Interval) {
        intervals = intervals.toMutableList().apply {
            if (position == null) add(interval)
            else {
                val old = get(position)
                set(position, interval.copy(groupId = old.groupId, groupName = old.groupName, groupRepetitions = old.groupRepetitions))
            }
        }
        save()
    }
    fun remove(position: Int) { intervals = intervals.toMutableList().apply { removeAt(position) }; save() }
    fun move(position: Int, delta: Int) {
        val blocks = intervals.blocks().toMutableList()
        var offset = 0
        val source = blocks.indexOfFirst { block ->
            val contains = position in offset until offset + block.size
            offset += block.size
            contains
        }
        val target = source + delta
        if (source < 0 || target !in blocks.indices) return
        blocks.add(target, blocks.removeAt(source))
        intervals = blocks.flatten()
        save()
    }
    fun group(positions: Set<Int>, title: String, count: Int, existingId: String? = null) {
        if (positions.isEmpty() || count !in 1..99) return
        val range = positions.min()..positions.max()
        if (positions.size != range.count() || range.any { it !in intervals.indices }) return
        if (range.any { intervals[it].groupId != null && intervals[it].groupId != existingId }) return
        val id = existingId ?: UUID.randomUUID().toString()
        intervals = intervals.mapIndexed { i, interval ->
            when {
                i in positions -> interval.copy(groupId = id, groupName = title.trim().ifEmpty { "Interval group" }, groupRepetitions = count)
                interval.groupId == id -> interval.copy(groupId = null, groupRepetitions = 1)
                else -> interval
            }
        }
        save()
    }
    fun ungroup(id: String) {
        intervals = intervals.map { if (it.groupId == id) it.copy(groupId = null, groupRepetitions = 1) else it }
        save()
    }
    fun moveMember(position: Int, delta: Int) {
        val target = position + delta
        val id = intervals.getOrNull(position)?.groupId ?: return
        if (intervals.getOrNull(target)?.groupId != id) return
        intervals = intervals.toMutableList().apply { add(target, removeAt(position)) }
        save()
    }
    fun start() {
        if (intervals.isEmpty()) return
        save()
        session = intervals.blocks().flatMap { block ->
            (1..block.first().groupRepetitions).flatMap { round -> block.map { it.copy(groupRound = round) } }
        }
        index = 0; repetition = 1; paused = false
        remaining = intervals.first().seconds
        deadline = SystemClock.elapsedRealtime() + remaining * 1000L
        announceCurrent()
    }
    fun toggle() {
        if (paused) deadline = SystemClock.elapsedRealtime() + remaining * 1000L
        else remaining = ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999).div(1000).toInt()
        paused = !paused
        if (paused) cuePlayer.stop() else announceCurrent()
    }
    fun skip() {
        val steps = session ?: return
        if (complete) return
        index++
        repetition = 1
        remaining = steps.getOrNull(index)?.seconds ?: 0
        deadline = SystemClock.elapsedRealtime() + remaining * 1000L
        announceCurrent()
    }
    fun finish() { cuePlayer.stop(); session = null }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = green, background = Color(0xFFF7F8F2), surface = Color.White, onSurface = ink)) {
                RunApp()
            }
        }
    }
}

@Composable
private fun RunApp(model: RunModel = viewModel()) {
    var editing by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var endPrompt by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var audioSettings by remember { mutableStateOf(false) }
    val steps = model.session
    val view = LocalView.current
    val running = steps != null && !model.complete && !model.paused
    DisposableEffect(view, running) {
        val previous = view.keepScreenOn
        if (running) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                    if (steps == null) {
                        Button(onClick = model::start, enabled = model.intervals.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text("Start run  →", fontSize = 17.sp)
                        }
                    } else if (model.complete) {
                        Button(onClick = model::finish, modifier = Modifier.fillMaxWidth()) { Text("Back to my plan") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = model::toggle, modifier = Modifier.weight(1f)) { Text(if (model.paused) "Resume" else "Pause") }
                            OutlinedButton(onClick = model::skip, modifier = Modifier.weight(1f)) { Text("Skip interval") }
                        }
                        TextButton(onClick = { endPrompt = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("End run") }
                    }
                }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("STRIDE  /  INTERVAL TRAINING", color = green, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))
                Text(if (steps == null) "Your pace.\nYour plan." else if (model.complete) "Run complete." else "Find your rhythm.", fontSize = 36.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, color = ink)
                Spacer(Modifier.height(8.dp))
                Text(if (steps == null) "Build a little structure into your next run." else if (model.complete) "You made time to move. Nicely done." else "Keep this screen open to follow your intervals.", color = green)
            }
            item {
                TextButton(onClick = { audioSettings = true }) { Text(if (model.audioEnabled) "Audio cues: On" else "Audio cues: Off") }
            }
            if (steps == null) {
                item {
                    OutlinedTextField(value = model.name, onValueChange = { model.name = it.take(60); model.save() }, label = { Text("Workout name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ink), shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("TOTAL TIME", color = Color(0xFFB8D3C9), fontSize = 11.sp); Text(clock(model.intervals.totalSeconds()), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                            Column { Text("INTERVALS", color = Color(0xFFB8D3C9), fontSize = 11.sp); Text("${model.intervals.size}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("The game plan", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { editIndex = null; editing = true }, enabled = model.intervals.size < 100) { Text("+ Add interval") }
                    }
                }
                item {
                    OutlinedButton(onClick = { groupId = null; grouping = true }, enabled = model.intervals.any { it.groupId == null }, modifier = Modifier.fillMaxWidth()) { Text("+ Group intervals") }
                }
                itemsIndexed(model.intervals) { i, interval ->
                    if (interval.groupId != null && model.intervals.getOrNull(i - 1)?.groupId != interval.groupId) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE1EDE7)), modifier = Modifier.padding(bottom = 8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(interval.groupName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("Repeat group: ${interval.groupRepetitions} times", color = green)
                                Row {
                                    TextButton(onClick = { groupId = interval.groupId; grouping = true }) { Text("Edit group") }
                                    TextButton(onClick = { model.ungroup(interval.groupId) }) { Text("Ungroup") }
                                }
                                Row {
                                    TextButton(onClick = { model.move(i, -1) }, enabled = i > 0) { Text("Group up") }
                                    TextButton(onClick = { model.move(i, 1) }, enabled = model.intervals.last().groupId != interval.groupId) { Text("Group down") }
                                }
                            }
                        }
                    }
                    Card(modifier = Modifier.padding(start = if (interval.groupId != null) 16.dp else 0.dp), onClick = { editIndex = i; editing = true }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(tint(interval.kind), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("%02d".format(i + 1), fontWeight = FontWeight.Bold, color = ink) }
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(interval.kind, fontWeight = FontWeight.Bold); Text("Tap to edit", color = green, fontSize = 12.sp) }
                                Text(clock(interval.seconds), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            }
                            if (interval.repetitions > 1) Text("${interval.repetitions} times · ${clock(interval.seconds)} each", color = green, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { if (interval.groupId == null) model.move(i, -1) else model.moveMember(i, -1) }, enabled = i > 0 && (interval.groupId == null || model.intervals[i - 1].groupId == interval.groupId)) { Text("Up") }
                                TextButton(onClick = { if (interval.groupId == null) model.move(i, 1) else model.moveMember(i, 1) }, enabled = i < model.intervals.lastIndex && (interval.groupId == null || model.intervals[i + 1].groupId == interval.groupId)) { Text("Down") }
                                TextButton(onClick = { model.remove(i) }) { Text("Remove") }
                            }
                        }
                    }
                }
                item { Text("Your plan saves automatically on this device.", color = green, fontSize = 12.sp) }
            } else if (!model.complete) {
                item {
                    val currentInterval = steps.getOrNull(model.index) ?: return@item
                    Card(colors = CardDefaults.cardColors(containerColor = tint(currentInterval.kind)), shape = RoundedCornerShape(28.dp)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("INTERVAL ${model.index + 1} OF ${steps.size}", letterSpacing = 2.sp, fontSize = 12.sp)
                            if (currentInterval.groupId != null) Text("${currentInterval.groupName} · round ${currentInterval.groupRound} of ${currentInterval.groupRepetitions}", color = green)
                            Spacer(Modifier.height(28.dp))
                            Text(currentInterval.kind, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            if (currentInterval.repetitions > 1) Text("REPETITION ${model.repetition} OF ${currentInterval.repetitions}", fontSize = 12.sp, color = green)
                            Text(clock(model.remaining), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                            Text(if (model.paused) "PAUSED" else "TIME REMAINING", fontSize = 12.sp)
                            Spacer(Modifier.height(28.dp))
                            LinearProgressIndicator(progress = { 1f - model.remaining.toFloat() / currentInterval.seconds }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    val current = steps.getOrNull(model.index)
                    val next = if (current != null && model.repetition < current.repetitions)
                        "Up next: ${current.kind} · repetition ${model.repetition + 1} of ${current.repetitions}"
                    else steps.getOrNull(model.index + 1)?.let { "Up next: ${it.kind} · ${clock(it.seconds)}" } ?: "This is your final interval."
                    Text(next, color = green)
                    if ((current?.repetitions ?: 1) > 1) Text("Skip interval moves past all remaining repetitions.", color = green, fontSize = 12.sp)
                }
            }
        }
    }
    if (audioSettings) AudioCueDialog(model.audioEnabled, model.audioStatus, steps == null, model::changeAudioEnabled, model::previewCue) {
        model.stopPreview(); audioSettings = false
    }
    if (grouping) GroupDialog(model.intervals, groupId, onDismiss = { grouping = false }) { positions, title, count ->
        model.group(positions, title, count, groupId); grouping = false
    }
    if (editing) IntervalDialog(editIndex?.let { model.intervals[it] }, onDismiss = { editing = false }) {
        model.update(editIndex, it); editing = false
    }
    if (endPrompt) AlertDialog(onDismissRequest = { endPrompt = false }, title = { Text("End this run?") }, text = { Text("Your interval plan will be saved. This session will end.") },
        confirmButton = { TextButton(onClick = { model.finish(); endPrompt = false }) { Text("End run") } },
        dismissButton = { TextButton(onClick = { endPrompt = false }) { Text("Keep going") } })
}

@Composable
private fun IntervalDialog(initial: Interval?, onDismiss: () -> Unit, onSave: (Interval) -> Unit) {
    var kind by remember { mutableStateOf(initial?.kind ?: "Run") }
    var minutes by remember { mutableStateOf(((initial?.seconds ?: 60) / 60).toString()) }
    var seconds by remember { mutableStateOf(((initial?.seconds ?: 60) % 60).toString()) }
    var repetitions by remember { mutableStateOf((initial?.repetitions ?: 1).toString()) }
    val r = repetitions.toIntOrNull()
    val m = minutes.toIntOrNull()
    val s = seconds.toIntOrNull()
    val durationValid = m != null && s != null && m in 0..99 && s in 0..59 && m * 60 + s > 0
    val repeatsValid = r != null && r in 1..99
    val valid = durationValid && repeatsValid
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add interval" else "Edit interval") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            kinds.forEach { option ->
                FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(minutes, { if (it.length <= 2 && it.all(Char::isDigit)) minutes = it }, label = { Text("Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(seconds, { if (it.length <= 2 && it.all(Char::isDigit)) seconds = it }, label = { Text("Seconds") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(repetitions, { if (it.length <= 2 && it.all(Char::isDigit)) repetitions = it }, label = { Text("Times to perform") }, supportingText = { Text("1 = once. Complete every repetition before the next interval.") }, isError = !repeatsValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (!durationValid) Text("Enter 1 second to 99 minutes, 59 seconds.", color = MaterialTheme.colorScheme.error)
            if (!repeatsValid) Text("Enter 1 to 99 times.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(Interval(kind, m!! * 60 + s!!, r!!)) }) { Text("Save interval") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
