package com.example.stride

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RunDeviceTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var prefs: SharedPreferences
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var oldName: String? = null
    private var oldPlan: String? = null
    private var oldAudio: Boolean? = null

    @Before fun prepare() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("plan", 0)
        oldName = prefs.getString("name", null)
        oldPlan = prefs.getString("intervals", null)
        oldAudio = if (prefs.contains("audioEnabled")) prefs.getBoolean("audioEnabled", true) else null
    }
    private fun launch(vararg steps: Pair<String, Int>) {
        val array = JSONArray()
        steps.forEach { (kind, seconds) -> array.put(JSONObject().put("kind", kind).put("seconds", seconds)) }
        prefs.edit().putString("name", "Device test").putString("intervals", array.toString()).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun restore() {
        if (::scenario.isInitialized) scenario.close()
        val edit = prefs.edit().putString("name", oldName).putString("intervals", oldPlan)
        if (oldAudio == null) edit.remove("audioEnabled") else edit.putBoolean("audioEnabled", oldAudio!!)
        edit.commit()
    }
    @Test fun editValidateAddReorderRemoveAndPersist() {
        launch("Warm-up" to 60)
        ui.onNodeWithText("Tap to edit").performClick()
        ui.onNodeWithText("Minutes").performTextReplacement("0")
        ui.onNodeWithText("Seconds").performTextReplacement("0")
        ui.onNodeWithText("Save interval").assertIsNotEnabled()
        ui.onNodeWithText("Seconds").performTextReplacement("60")
        ui.onNodeWithText("Save interval").assertIsNotEnabled()
        ui.onNodeWithText("Seconds").performTextReplacement("5")
        ui.onNodeWithText("Save interval").performClick()
        ui.onAllNodesWithText("00:05").assertCountEquals(2)
        ui.onNodeWithText("+ Add interval").performClick()
        ui.onNodeWithText("Jog").performClick()
        ui.onNodeWithText("Save interval").performClick()
        ui.onAllNodesWithText("Down")[0].performClick()
        ui.runOnIdle {
            val array = JSONArray(prefs.getString("intervals", "[]"))
            org.junit.Assert.assertEquals("Jog", array.getJSONObject(0).getString("kind"))
        }
        ui.onAllNodesWithText("Remove")[0].performClick()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNodeWithText("Device test").assertExists()
        ui.onAllNodesWithText("00:05").assertCountEquals(2)
        ui.onNodeWithText("Jog").assertDoesNotExist()
    }
    @Test fun pauseResumeRotationSkipAndComplete() {
        launch("Run" to 30, "Recovery" to 30)
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("INTERVAL 1 OF 2").assertExists()
        ui.onNodeWithText("Pause").performClick()
        ui.onNodeWithText("PAUSED").assertExists()
        val time = ui.onAllNodes(hasText("00:", substring = true))[0].fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text][0].text
        Thread.sleep(1200)
        ui.onNodeWithText(time).assertExists()
        scenario.recreate()
        ui.onNodeWithText("PAUSED").assertExists()
        ui.onNodeWithText("Resume").performClick()
        ui.onNodeWithText("TIME REMAINING").assertExists()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("INTERVAL 2 OF 2").assertExists()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Run complete.").assertExists()
        ui.onNodeWithText("Back to my plan").performClick()
        ui.onNodeWithText("Start run  →").assertExists()
    }
    @Test fun automaticallyAdvancesAndCompletes() {
        launch("Run" to 2, "Recovery" to 2)
        ui.onNodeWithText("Start run  →").performClick()
        ui.waitUntil(6000) { ui.onAllNodesWithText("INTERVAL 2 OF 2").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(6000) { ui.onAllNodesWithText("Run complete.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun repetitionsSaveAndAutomaticallyAdvance() {
        launch("Run" to 3, "Recovery" to 2)
        ui.onAllNodesWithText("Tap to edit")[0].performClick()
        ui.onNodeWithText("Times to perform").performScrollTo().performTextReplacement("0")
        ui.onNodeWithText("Save interval").assertIsNotEnabled()
        ui.onNodeWithText("Times to perform").performTextReplacement("")
        ui.onNodeWithText("Save interval").assertIsNotEnabled()
        ui.onNodeWithText("Times to perform").performTextReplacement("2")
        ui.onNodeWithText("Save interval").performClick()
        ui.onNodeWithText("00:08").assertExists()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("2 times · 00:03 each"))
        ui.onNodeWithText("2 times · 00:03 each").assertExists()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("2 times · 00:03 each"))
        ui.onNodeWithText("2 times · 00:03 each").assertExists()
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("REPETITION 1 OF 2").assertExists()
        ui.waitUntil(6000) { ui.onAllNodesWithText("REPETITION 2 OF 2").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("INTERVAL 1 OF 2").assertExists()
        ui.waitUntil(6000) { ui.onAllNodesWithText("INTERVAL 2 OF 2").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(6000) { ui.onAllNodesWithText("Run complete.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun repeatedIntervalRetainsStateAndSkipMovesToNextInterval() {
        launch("Run" to 30, "Recovery" to 30)
        ui.onAllNodesWithText("Tap to edit")[0].performClick()
        ui.onNodeWithText("Times to perform").performScrollTo().performTextReplacement("3")
        ui.onNodeWithText("Save interval").performClick()
        ui.onNodeWithText("02:00").assertExists()
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("Pause").performClick()
        scenario.recreate()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("REPETITION 1 OF 3"))
        ui.onNodeWithText("REPETITION 1 OF 3").assertExists()
        ui.onNodeWithText("PAUSED").assertExists()
        ui.onNodeWithText("Resume").performClick()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("INTERVAL 2 OF 2").assertExists()
        ui.onNodeWithText("REPETITION 1 OF 3").assertDoesNotExist()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Run complete.").assertExists()
    }
    private fun plannerScrollTo(text: String) {
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
    private fun createPairGroup() {
        plannerScrollTo("+ Group intervals")
        ui.onNodeWithText("+ Group intervals").performClick()
        ui.onNodeWithText("Select interval 1: Run").performScrollTo().performClick()
        ui.onNodeWithText("Select interval 2: Recovery").performScrollTo().performClick()
        ui.onNodeWithText("Save group").performClick()
    }
    @Test fun groupValidationPersistenceMoveEditAndUngroup() {
        launch("Run" to 30, "Recovery" to 30, "Cool-down" to 30)
        plannerScrollTo("+ Group intervals")
        ui.onNodeWithText("+ Group intervals").performClick()
        ui.onNodeWithText("Save group").assertIsNotEnabled()
        ui.onNodeWithText("Select interval 1: Run").performScrollTo().performClick()
        ui.onNodeWithText("Select interval 3: Cool-down").performScrollTo().performClick()
        ui.onNodeWithText("Save group").assertIsNotEnabled()
        ui.onNodeWithText("Select interval 3: Cool-down").performClick()
        ui.onNodeWithText("Select interval 2: Recovery").performScrollTo().performClick()
        ui.onNodeWithText("Group times to perform").performScrollTo().performTextReplacement("0")
        ui.onNodeWithText("Save group").assertIsNotEnabled()
        ui.onNodeWithText("Group times to perform").performTextReplacement("2")
        ui.onNodeWithText("Save group").performClick()
        plannerScrollTo("02:30")
        ui.onNodeWithText("02:30").assertExists()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        plannerScrollTo("Repeat group: 2 times")
        ui.onNodeWithText("Repeat group: 2 times").assertExists()
        ui.onNodeWithText("Group down").performScrollTo().performClick()
        ui.runOnIdle {
            val array = JSONArray(prefs.getString("intervals", "[]"))
            org.junit.Assert.assertEquals("Cool-down", array.getJSONObject(0).getString("kind"))
            org.junit.Assert.assertEquals(array.getJSONObject(1).getString("groupId"), array.getJSONObject(2).getString("groupId"))
        }
        plannerScrollTo("Edit group")
        ui.onNodeWithText("Edit group").performClick()
        ui.onNodeWithText("Group times to perform").performScrollTo().performTextReplacement("3")
        ui.onNodeWithText("Save group").performClick()
        plannerScrollTo("Repeat group: 3 times")
        ui.onNodeWithText("Repeat group: 3 times").assertExists()
        ui.onNodeWithText("Ungroup").performScrollTo().performClick()
        plannerScrollTo("01:30")
        ui.onNodeWithText("01:30").assertExists()
        ui.runOnIdle {
            val array = JSONArray(prefs.getString("intervals", "[]"))
            repeat(3) { org.junit.Assert.assertFalse(array.getJSONObject(it).has("groupId")) }
        }
    }
    @Test fun groupRoundsSkipAndRecreationFollowSequence() {
        launch("Run" to 30, "Recovery" to 30, "Cool-down" to 30)
        createPairGroup()
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("Run + recovery · round 1 of 2").assertExists()
        ui.onNodeWithText("INTERVAL 1 OF 5").assertExists()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Recovery").assertExists()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Run + recovery · round 2 of 2").assertExists()
        ui.onNodeWithText("Run").assertExists()
        ui.onNodeWithText("Pause").performClick()
        scenario.recreate()
        plannerScrollTo("Run + recovery · round 2 of 2")
        ui.onNodeWithText("PAUSED").assertExists()
        ui.onNodeWithText("Resume").performClick()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Recovery").assertExists()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Cool-down").assertExists()
        ui.onNodeWithText("Run + recovery · round 2 of 2").assertDoesNotExist()
        ui.onNodeWithText("Skip interval").performClick()
        ui.onNodeWithText("Run complete.").assertExists()
    }
    @Test fun groupAutomaticallyRepeatsIncludingIntervalRepetitions() {
        launch("Run" to 2, "Recovery" to 2, "Cool-down" to 2)
        // Configure a nested interval repeat, then exercise the group builder through the UI.
        scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[RunModel::class.java].update(0, Interval("Run", 2, 2))
        }
        createPairGroup()
        plannerScrollTo("00:14")
        ui.onNodeWithText("00:14").assertExists()
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("REPETITION 1 OF 2").assertExists()
        ui.waitUntil(5000) { ui.onAllNodesWithText("REPETITION 2 OF 2").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(5000) { ui.onAllNodesWithText("Recovery").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(5000) { ui.onAllNodesWithText("Run + recovery · round 2 of 2").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Run").assertExists()
        ui.onNodeWithText("REPETITION 1 OF 2").assertExists()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Cool-down").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(5000) { ui.onAllNodesWithText("Run complete.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun audioControlsPreviewAndPersist() {
        launch("Run" to 30)
        ui.onNodeWithText("Audio cues: On").performClick()
        ui.onNodeWithContentDescription("Enable audio cues").assertIsOn()
        CueType.entries.forEach { cue ->
            ui.onNodeWithContentDescription("Preview ${cue.label}").performScrollTo().performClick()
            Thread.sleep(850)
        }
        ui.onNodeWithContentDescription("Enable audio cues").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Enable audio cues").assertIsOff()
        ui.onNodeWithContentDescription("Preview Warm-up").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Done").performClick()
        ui.onNodeWithText("Audio cues: Off").assertExists()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNodeWithText("Audio cues: Off").assertExists()
        ui.onNodeWithText("Audio cues: Off").performClick()
        ui.onNodeWithContentDescription("Enable audio cues").performClick()
        ui.onNodeWithText("Done").performClick()
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("Audio cues: On").performClick()
        ui.onNodeWithContentDescription("Preview Run").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Done").performClick()
        ui.onNodeWithText("Pause").performClick()
    }
    @Test fun endConfirmationAndEmptyPlan() {
        launch("Run" to 60)
        ui.onNodeWithText("Start run  →").performClick()
        ui.onNodeWithText("End run").performClick()
        ui.onNodeWithText("Keep going").performClick()
        ui.onNodeWithText("TIME REMAINING").assertExists()
        ui.onNodeWithText("End run").performClick()
        ui.onAllNodesWithText("End run").onLast().performClick()
        ui.onNodeWithText("Remove").performClick()
        ui.onNodeWithText("Start run  →").assertIsNotEnabled()
    }
}
