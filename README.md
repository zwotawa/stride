# Stride

A native Android interval-run planner built with Kotlin and Jetpack Compose.

## Use

Name your workout, add warm-up, run, jog, recovery, or cool-down intervals, and set each duration. Tap a step to edit it. Use Up and Down to reorder steps. Plans save automatically on the device.

Set Times to perform (1–99) when adding or editing an interval. This is the total number of times it runs before the next interval, and the total workout time includes all repetitions. Existing saved intervals default to once.

Start run begins the sequence. The timer shows the current repetition. Pause, resume, skip an entire interval (including its remaining repetitions), or end the session from the timer screen.

Use + Group intervals to select consecutive intervals, name the group, and set 1–99 rounds. Each round performs those intervals in order, including their individual repetitions. The total time includes all rounds. Edit group changes the selection or round count. Group up/down moves the whole group; Up/Down on a member reorders it within the group. Ungroup keeps all member intervals. Groups cannot overlap or contain other groups. Skip interval skips the current member and its individual repetitions; subsequent members and rounds still run.

## Audio cues

Open Audio cues from the planner or timer. Sound is enabled by default and the setting saves on the device. Preview each cue before a run. Previews are disabled during a session.

- Warm-up: one low tone.
- Run: two high tones.
- Jog: one medium tone.
- Recovery: two low tones.
- Cool-down: high then low.
- Completion: three rising tones.

Each transition also speaks the interval name, plus repetition and group round counts when applicable, if the device has an installed offline English text-to-speech voice. Otherwise tones remain available and the dialog explains the fallback. Cues use media volume and the current audio route; they do not override muted volume or force the speaker. Audio focus is requested briefly with ducking so other media can lower its volume. New cues replace stale speech, and pause, mute, end, and model disposal stop playback.

Keep the app open during the workout. The screen stays awake while the timer runs. This version does not run a foreground service for background or screen-locked cues.

## Build

Open the project in Android Studio and sync Gradle, then run the app configuration on an emulator or device (Android 7.0 or newer). To build an APK on Windows, run `./gradlew.bat assembleDebug`.

## Current scope

One saved time-based plan. The timer uses monotonic elapsed time and retains its session during rotation. Active sessions are not restored after process termination. This version has no background service, GPS, or run history; keep the app open for the timer and audio cues.
