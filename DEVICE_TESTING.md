# Emulator testing

Device: Medium_Phone_API_37.0, emulator-5554, Android 17 / API 37.

Result: 13 instrumented tests passed, 0 failures on the final run.

Jog coverage: select Jog in the interval editor, save and reload the plan, preview its audio cue, and announce Jog through automatic interval repetitions and group rounds.

Audio coverage: cue dispatch order across all five interval types, nested interval repetitions and group rounds, exactly one completion event, pause/resume, silent paused skipping, mute, preview restrictions during a run, stopping on end, preference persistence, and releasing audio resources on model disposal. Event-order tests use a recording CuePlayer; a separate UI test invokes every real preview and checks the saved sound toggle. The audio dialog was visually inspected. The emulator reports an available offline voice; a manual Run preview produced AudioTrack frame-delivery logs for both the app tone and speech engine without an unavailable/busy status. Acoustic quality and physical headphone routing were not assessed.

Group coverage: reject empty and nonconsecutive selections and zero rounds; create, edit, save, reload, move a group as a unit, and ungroup without deleting members. Verify total duration, group round sequence, skip behavior, paused activity recreation, automatic group looping with individual repetitions, and continuation to the following standalone interval. The group builder was visually inspected on the emulator.

Repeat-count coverage: reject zero and empty counts, save and reload counts, include repeats in total duration, automatically repeat before advancing and completing, retain repeat state through activity recreation, and skip all remaining repetitions.

- Interval editing, zero-duration and invalid-second validation, adding, reordering, removing, and loading the saved plan in a new activity.
- Start, pause (countdown stays unchanged), resume, activity recreation with the session retained, skip, completion, and returning to the planner.
- Automatic transition and completion using two short intervals.
- Cancel and confirm end-run actions, plus disabled Start for an empty plan.

Initial testing found an IndexOutOfBoundsException at run completion: the deferred progress callback referenced the changing interval index. The UI now captures the current interval safely. The regression tests pass for both natural completion and skipping the final interval.

The planner and interval editor were also visually inspected on the emulator. Test setup restores the prior saved plan after each test. The app was reopened after testing.

Run again with:

```powershell
./gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.stride.RunDeviceTest,com.example.stride.RunAudioTest" --console=plain
```

Scope: one phone emulator in portrait. Activity recreation and foreground audio dispatch/playback controls were tested. Physical rotation, other screen sizes, physical speakers/headphones and acoustic quality, background operation, and process-death recovery were not tested. Keep the app open for cues; there is no background timer service.
