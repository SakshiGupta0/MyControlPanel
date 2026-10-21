package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Derive
import app.mycontrolpanel.core.Env
import app.mycontrolpanel.core.Focus
import app.mycontrolpanel.core.Task
import app.mycontrolpanel.core.TaskFilter
import app.mycontrolpanel.core.Validation
import kotlinx.coroutines.delay

// ============================ Tasks screen ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(s: AppState, filter: String, env: Env, dispatch: Dispatch) {
    val p = P()
    val filters = listOf(TaskFilter.TODAY to "★  Today", TaskFilter.INBOX to "📥  Inbox", TaskFilter.ALL to "All open") +
        s.data.modes.sortedBy { it.order }.map { it.id to "${it.icon}  ${it.name}" } +
        s.live.spaces.map { "space:${it.id}" to "🏠  ${it.name}" } + listOf(TaskFilter.DONE to "✓  Done")
    val list = Derive.tasksFor(s, filter, env)
    val win = LocalWindow.current
    val body: @Composable () -> Unit = {
            QuickAdd(filter, dispatch)
            when (filter) {
                TaskFilter.TODAY -> T("Up to ${s.data.prefs.todayLimit} things. Star a task (☆) to put it here.", Type.label, p.muted)
                TaskFilter.INBOX -> T("Everything you brain-dumped. Star it for today, or tap it to give it a mode.", Type.label, p.muted)
                else -> if (filter.startsWith("space:")) {
                    val sp = s.live.spaces.find { it.id == filter.removePrefix("space:") }
                    if (sp != null) T("Shared with ${sp.members.values.joinToString(", ")}. Everyone sees ticks and new tasks right away.", Type.label, p.muted)
                }
            }
            PanelCard(Modifier.fillMaxWidth().perch("list")) {
                Column {
                    if (list.isEmpty()) Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (LocalPerches.current == null) Bean(if (filter == TaskFilter.INBOX) BeanMood.HAPPY else BeanMood.CALM, size = 64.dp)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
                        T(when (filter) {
                            TaskFilter.TODAY -> "Nothing picked for today yet. Mochi suggests one small thing."
                            TaskFilter.INBOX -> "Inbox is empty. Your head is clear 🌿"
                            TaskFilter.DONE -> "Finished tasks show up here. Mochi is cheering already."
                            else -> "Nothing here. Cosy."
                        }, Type.body, p.muted)
                    }
                    list.forEach { t -> TaskRow(t, s, dispatch) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filter == TaskFilter.TODAY && Derive.todayTasks(s).isNotEmpty()) SecondaryButton("Fresh start (clear today)", { dispatch(Action.FreshStart) })
                if (filter == TaskFilter.DONE && list.isNotEmpty()) SecondaryButton("Clear finished", { dispatch(Action.ClearDone) })
                SecondaryButton("Pick one for me", { dispatch(Action.PickOne) })
            }
            Spacer(Modifier.height(BubbleRoom))
    }
    if (win.compact) {
        Column(Modifier.fillMaxSize().background(p.bg2.copy(alpha = if (p.dark) 0.80f else 0.84f)).testTag("tasksScreen")) {
            Column(Modifier.fillMaxWidth().background(p.cardStrong).padding(horizontal = win.gutter, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("‹  Dashboard", { dispatch(Action.GoDashboard) }, tag = "closeTasks")
                Row(Modifier.horizontalScroll(rememberScrollState()).perch("tabs"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { (id, label) -> Chip(label, id == filter, { dispatch(Action.OpenTasks(id)) }, tag = "filter_$id") }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = win.gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) { body() }
        }
        return
    }
    Row(Modifier.fillMaxSize().background(p.bg2.copy(alpha = if (p.dark) 0.80f else 0.84f)).testTag("tasksScreen")) {
        Column(Modifier.width(260.dp).fillMaxHeight().background(p.cardStrong).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton("‹  Dashboard", { dispatch(Action.GoDashboard) }, Modifier.fillMaxWidth(), tag = "closeTasks")
            Spacer(Modifier.height(14.dp))
            filters.forEach { (id, label) ->
                val sel = id == filter
                val count = if (id == TaskFilter.DONE) null else Derive.tasksFor(s, id, env).count { !it.done }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) p.card else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { dispatch(Action.OpenTasks(id)) }.padding(horizontal = 14.dp).testTag("filter_$id"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(label, if (sel) Type.heading else Type.label, if (sel) p.text else p.muted, Modifier.weight(1f))
                    if (count != null && count > 0) T("$count", Type.small, p.muted)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) { body() }
    }
}

/** Inline add box at the top of the list — type and press Add (or the mic). */
@Composable
private fun QuickAdd(filter: String, dispatch: Dispatch) {
    val platform = LocalPlatform.current
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { PanelTextField(text, { text = it }, "Add a task…", tag = "quickAddField") }
        if (platform.voiceAvailable) RoundIconButton("🎙", { platform.listen { text = it } }, size = 56.dp)
        PrimaryButton("Add", {
            dispatch(Action.Capture(text, today = filter == TaskFilter.TODAY, spaceId = filter.takeIf { it.startsWith("space:") }?.removePrefix("space:"))); text = ""
        }, tag = "quickAddButton", enabled = text.isNotBlank())
    }
}

// ============================ Brain dump ============================

@Composable
fun CaptureDialog(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    var text by remember { mutableStateOf("") }
    var today by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = { dispatch(Action.CloseDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 820.dp).padding(28.dp).testTag("captureDialog"),
        containerColor = p.card,
        title = { T("What's on your mind?", Type.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Get it out of your head. Sort it later.", Type.label, p.muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        PanelTextField(text, { text = it }, "Type it…", Modifier.focusRequester(focus), singleLine = false, tag = "captureField")
                    }
                    if (platform.voiceAvailable) RoundIconButton("🎙", { platform.listen { heard -> text = if (text.isBlank()) heard else "$text $heard" } }, size = 60.dp, tag = "captureMic")
                }
                ToggleRow("Add to today's list", today, { today = it }, sub = if (Derive.todayTasks(s).size >= s.data.prefs.todayLimit) "Today's list is full — it'll go to the inbox" else null)
            }
        },
        confirmButton = { PrimaryButton("Save", { dispatch(Action.Capture(text, today)) }, tag = "captureSave", enabled = text.isNotBlank()) },
        dismissButton = { SecondaryButton("Cancel", { dispatch(Action.CloseDialog) }) },
    )
}

// ============================ Task editor ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(s: AppState, task: Task, isNew: Boolean, dispatch: Dispatch) {
    val p = P()
    fun set(t: Task) = dispatch(Action.UpdateTaskDraft(t))
    val problem = Validation.task(task).firstOrNull()
    AlertDialog(
        onDismissRequest = { dispatch(Action.CloseDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 860.dp).padding(28.dp).testTag("taskDialog"),
        containerColor = p.card,
        title = { T(if (isNew) "New task" else "Task", Type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelTextField(task.title, { set(task.copy(title = it)) }, "What needs doing?", tag = "taskTitleField")
                PanelTextField(task.notes, { set(task.copy(notes = it)) }, "Notes or the very first tiny step (optional)", singleLine = false)
                ToggleRow("★  On today's list", task.today, { set(task.copy(today = it)) })
                if (!isNew) ToggleRow("Done", task.done, { set(task.copy(done = it, doneAt = if (it) s.now else null)) })
                if (s.live.spaces.isNotEmpty() || task.spaceId != null) {
                    T("List", Type.label, p.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("🔒 Just me", task.spaceId == null, { set(task.copy(spaceId = null)) })
                        s.live.spaces.forEach { sp -> Chip("🏠 ${sp.name}", task.spaceId == sp.id, { set(task.copy(spaceId = sp.id, googleId = null)) }) }
                    }
                }
                T("Show in modes", Type.label, p.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.data.modes.sortedBy { it.order }.forEach { m ->
                        val on = m.id in task.modeIds
                        Chip("${m.icon} ${m.name}", on, { set(task.copy(modeIds = if (on) task.modeIds - m.id else task.modeIds + m.id)) })
                    }
                }
                if (problem != null) T(problem, Type.small, p.warn)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) QuietButton("Delete", { dispatch(Action.DeleteTask(task.id)) }, color = p.danger, tag = "deleteTask")
                PrimaryButton("Save", { dispatch(Action.SaveTask) }, tag = "saveTask", enabled = problem == null)
            }
        },
        dismissButton = { SecondaryButton("Cancel", { dispatch(Action.CloseDialog) }) },
    )
}

// ============================ Focus ============================

/** Visual focus timer: a ring that empties, one label, three calm controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(s: AppState, dispatch: Dispatch) {
    val p = P()
    val f = s.data.focus
    if (f == null) { LaunchedEffect(Unit) { dispatch(Action.GoDashboard) }; return }
    val remaining = Focus.remaining(f, s.now)
    val tint = LocalModeTint.current
    val win = LocalWindow.current
    val heading: @Composable () -> Unit = {
        Eyebrow(if (f.finished) "Time's up" else "Focusing on", p.accent)
        T(f.label, if (win.compact) Type.display else Type.display.copy(fontSize = 52.sp, lineHeight = 60.sp), maxLines = 3)
        T(if (f.finished) "Nice work staying with it. Did you finish?" else "Just this. Everything else can wait.", Type.body, p.muted)
    }
    val controls: @Composable () -> Unit = {
        if (f.finished) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.taskId != null) PrimaryButton("Yes, it's done ✓", { dispatch(Action.EndFocus(true)) }, tag = "focusDone", tall = 64.dp, style = Type.heading)
                SecondaryButton(if (f.taskId != null) "Not yet" else "Back to dashboard", { dispatch(Action.EndFocus(false)) }, tag = "focusNotYet")
                SecondaryButton("+5 min", { dispatch(Action.FocusExtend(5)) })
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.runningSince != null) SecondaryButton("Pause", { dispatch(Action.FocusPause) }, tag = "focusPause")
                else PrimaryButton("Resume", { dispatch(Action.FocusResume) }, tag = "focusResume")
                SecondaryButton("+5 min", { dispatch(Action.FocusExtend(5)) })
                if (f.taskId != null) PrimaryButton("Done early ✓", { dispatch(Action.EndFocus(true)) }, tag = "focusDoneEarly")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("‹  Peek at dashboard", { dispatch(Action.GoDashboard) }, color = p.muted)
                HoldToExitButton({ dispatch(Action.EndFocus(false)) }, tag = "holdStopFocus", label = "Hold to stop")
            }
        }
    }
    val ringView: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { ring ->
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            ProgressRing(Focus.fraction(f, s.now), Modifier.fillMaxSize(), stroke = if (ring < 260.dp) 16.dp else 22.dp,
                color = if (f.finished) p.warm else p.accent, track = p.card.copy(alpha = 0.8f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val big = if (ring < 260.dp) 64.sp else 96.sp
                T(if (f.finished) "✓" else fmtTimer(remaining), Type.clock.copy(fontSize = big, lineHeight = big * 1.05f),
                    if (f.finished) p.warm else p.text, modifier = Modifier.testTag("focusTimer"), align = TextAlign.Center, maxLines = 1)
                if (!f.finished && f.runningSince == null) T("Paused", Type.label, p.muted)
            }
        }
    }
    val bg = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg2.copy(alpha = 0.25f), p.bg2.copy(alpha = 0.75f)))).testTag("focusScreen")
    if (win.twoColumn) {
        // Side by side: words and buttons on the left, the ring sized to fit the screen's height on the right.
        val ring = minOf(win.height - 48.dp, win.width * 0.42f, 440.dp)
        Row(bg.padding(horizontal = if (win.short) 20.dp else 56.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (win.short) 24.dp else 56.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                heading(); Spacer(Modifier.height(4.dp)); controls()
            }
            ringView(ring)
        }
    } else {
        val ring = minOf(win.width - win.gutter * 2, 420.dp, win.height * 0.42f)
        Column(bg.verticalScroll(rememberScrollState()).padding(horizontal = win.gutter, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { heading() }
            Spacer(Modifier.height(8.dp))
            ringView(ring)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { controls() }
        }
    }
}
