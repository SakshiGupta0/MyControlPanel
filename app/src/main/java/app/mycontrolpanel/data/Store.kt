package app.mycontrolpanel.data

import android.os.Handler
import android.os.Looper
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppData
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Effect
import app.mycontrolpanel.core.Env
import app.mycontrolpanel.core.Reduced
import app.mycontrolpanel.core.Reducer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The single source of truth. All changes go through dispatch() on the main thread:
 * reduce (pure) → publish state → persist if data changed → reschedule alarms if needed → run effects.
 * The UI renders from [state]; it never mutates state while rendering.
 */
class Store(
    private val repo: Repository,
    private val onRoutinesChanged: (AppData) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    fun env() = Env(ZoneId.systemDefault()) { UUID.randomUUID().toString().substring(0, 8) }

    private val _state: MutableStateFlow<AppState>
    val state: StateFlow<AppState> get() = _state

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 16)
    val effects: SharedFlow<Effect> get() = _effects

    init {
        val loaded = repo.load()
        _state = MutableStateFlow(Reducer.initial(loaded.data, clock(), env(), loaded.message))
        onRoutinesChanged(_state.value.data)
    }

    fun dispatch(action: Action) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { dispatch(action) }; return }
        val old = _state.value
        val result = try {
            Reducer.reduce(old, action, clock(), env())
        } catch (e: Exception) {
            // Never hang or crash on a bad action: keep the old state and say so.
            Reduced(old.copy(ui = old.ui.copy(message = "Something went wrong (${e.message}). Nothing was changed.")))
        }
        _state.value = result.state
        val data = result.state.data
        if (data != old.data) persist(data)
        if (data.routines != old.data.routines) onRoutinesChanged(data)
        for (e in result.effects) {
            if (e is Effect.RetrySave) persist(data) else _effects.tryEmit(e)
        }
    }

    private fun persist(data: AppData) {
        io.execute {
            val error = repo.save(data)
            if (error != null) main.post { dispatch(Action.SaveFailed(error)) }
            else if (_state.value.ui.saveError != null) main.post { dispatch(Action.SaveSucceeded) }
        }
    }
}
