package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.Ambient
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Mode
import app.mycontrolpanel.core.MusicPrefs
import app.mycontrolpanel.core.MusicProvider
import app.mycontrolpanel.data.SpotifyEngine
import app.mycontrolpanel.system.SoundService
import app.mycontrolpanel.data.SpotifyEngine.Status as SpStatus

private fun timerText(min: Int) = if (min == 0) "Until I stop it" else "$min min"

// ============================================================================
// Settings → Music
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    val m = s.data.prefs.music
    fun set(n: MusicPrefs) = dispatch(Action.SetMusic(n))

    T("Where music and calm sounds come from on this device. Nothing here needs a paid subscription.", Type.body, p.muted)
    MusicProvider.values().forEach { pr ->
        val on = m.provider == pr
        PanelCard(Modifier.fillMaxWidth().clip(CardShape).clickable { set(m.copy(provider = pr)) }.testTag("provider_${pr.name}"),
            tint = if (on) p.accentSoft else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(pr.icon, Type.title); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { T(pr.label, Type.heading); T(pr.blurb, Type.small, p.muted) }
                CheckCircle(on, { set(m.copy(provider = pr)) }, size = 34.dp)
            }
        }
    }

    when (m.provider) {
        MusicProvider.BUILT_IN -> PanelCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Your calm sound", Type.heading)
                AmbientChips(m.ambient) { set(m.copy(ambient = it)) }
                Stepper("Fade out after", m.sleepTimerMin, { set(m.copy(sleepTimerMin = it)) }, 0..180, step = 15, format = ::timerText, tag = "sleepTimer")
                SoundTestRow { platform.playAmbient(m.ambient, m.sleepTimerMin) }
            }
        }
        MusicProvider.DEVICE_FILE -> PanelCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Your sound file", Type.heading)
                T(if (m.fileUri.isBlank()) "No file chosen yet." else "🎵  ${m.fileName}", Type.label, p.muted)
                PrimaryButton(if (m.fileUri.isBlank()) "Choose a sound file" else "Choose a different file", { platform.pickAudioFile() }, tag = "pickAudio")
                Stepper("Fade out after", m.sleepTimerMin, { set(m.copy(sleepTimerMin = it)) }, 0..180, step = 15, format = ::timerText)
                if (m.fileUri.isNotBlank()) SoundTestRow { platform.playFile(m.fileUri, m.fileName, m.sleepTimerMin) }
            }
        }
        MusicProvider.APP -> PanelCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Your music app", Type.heading)
                val apps = remember { platform.musicApps() }
                if (apps.isEmpty()) T("No music apps found on this device. The play/pause buttons still control whatever is playing.", Type.label, p.muted)
                else FlowRowChips(apps.map { it.second }, apps.indexOfFirst { it.first == m.appPackage }, "musicApp") { i ->
                    set(m.copy(appPackage = apps[i].first, appName = apps[i].second))
                }
                T("Favourite playlist or station (optional) — in the app: Share → Copy link, then paste.", Type.small, p.muted)
                PanelTextField(m.link, { set(m.copy(link = it)) }, "Playlist link", tag = "musicLink")
                T("Play / pause / skip work with free plans too. No special permission needed.", Type.small, p.muted)
            }
        }
        MusicProvider.SPOTIFY -> SpotifySetup(s, m, ::set, dispatch)
        MusicProvider.NONE -> {}
    }

    if (m.provider != MusicProvider.BUILT_IN && m.provider != MusicProvider.NONE) PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Calm sounds for bedtime", Type.heading)
            T("The built-in sounds are always there in the Bedtime music widget, whatever you pick above.", Type.small, p.muted)
            AmbientChips(m.ambient) { set(m.copy(ambient = it)) }
        }
    }
    T("Show music on the dashboard by turning on the Music widget for a mode (Settings → Modes → Edit).", Type.small, p.muted)
}

@Composable
private fun SoundTestRow(play: () -> Unit) {
    val platform = LocalPlatform.current
    val st by SoundService.state.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (st.playing) SecondaryButton("■  Stop", { platform.stopSound() }, tag = "soundStop")
        else PrimaryButton("▶  Try it", play, tag = "soundPlay")
        if (st.playing) T(st.label, Type.small, P().muted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmbientChips(selected: Ambient, onPick: (Ambient) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Ambient.values().forEach { a -> Chip("${a.icon} ${a.label}", a == selected, { onPick(a) }, tag = "ambient_${a.name}") }
    }
}

@Composable
private fun SpotifySetup(s: AppState, m: MusicPrefs, set: (MusicPrefs) -> Unit, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    var draft by remember(m.spotifyClientId) { mutableStateOf(m.spotifyClientId) }
    var help by remember { mutableStateOf(m.spotifyClientId.isBlank()) }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Spotify connection", Type.heading)
            T("Optional. Spotify asks every app to use its own free developer key (a “Client ID”). Paste yours once and the music widget shows album art, song names and browsing. Playback control needs Spotify Premium.", Type.small, p.muted)
            PanelTextField(draft, { draft = it.trim() }, "Spotify Client ID", tag = "spotifyClientId")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Save", {
                    set(m.copy(spotifyClientId = draft))
                    dispatch(Action.ShowMessage(if (draft.isBlank()) "Spotify connection turned off." else "Saved. Tap Connect when you're ready."))
                }, tag = "spotifySave", enabled = draft != m.spotifyClientId)
                QuietButton(if (help) "Hide steps" else "How do I get one?", { help = !help })
            }
            if (help) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "1. Open developer.spotify.com/dashboard and log in → Create app.",
                    "2. Redirect URI: mycontrolpanel://spotify-callback",
                    "3. Tick “Android”. Package name: app.mycontrolpanel",
                    "4. SHA-1: 3C:3B:03:2A:DF:F0:4E:E1:7D:CB:32:32:35:24:C5:6F:6D:45:28:82",
                    "5. Save, copy the Client ID and paste it above.",
                    "6. For other people: Settings of the app → User Management → add their Spotify email.",
                ).forEach { T(it, Type.small, p.muted) }
                QuietButton("Open the Spotify dashboard", { platform.openUri("https://developer.spotify.com/dashboard") })
            }
            LocalSpotify.current?.let { eng ->
                val st = eng.state.collectAsState().value
                if (m.spotifyClientId.isNotBlank()) {
                    T("Status: " + when (st.status) {
                        SpStatus.CONNECTED -> "connected ✓"
                        SpStatus.NOT_INSTALLED -> "Spotify app isn't installed"
                        SpStatus.CONNECTING -> "connecting…"
                        else -> st.message ?: "not connected"
                    }, Type.label, p.muted)
                    if (st.status != SpStatus.CONNECTED) PrimaryButton("Connect Spotify", { eng.connect(interactive = true) }, tag = "spotifyConnect")
                }
            }
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Sleep playlist", Type.heading)
            T("In Spotify: ⋯ → Share → Copy link to playlist, then paste here.", Type.small, p.muted)
            PanelTextField(s.data.prefs.sleepAudioUri, { v -> dispatch(Action.UpdatePrefs(s.data.prefs.copy(sleepAudioUri = v))) }, "Playlist link", tag = "sleepUri")
        }
    }
}

// ============================================================================
// Dashboard widget
// ============================================================================

@Composable
fun MusicWidget(s: AppState, mode: Mode, dispatch: Dispatch) {
    val m = s.data.prefs.music
    val bedtime = mode.id == "bedtime"
    Eyebrow(if (bedtime) "Sleep sounds" else "Music")
    when {
        m.provider == MusicProvider.BUILT_IN || (bedtime && m.provider != MusicProvider.SPOTIFY && m.provider != MusicProvider.DEVICE_FILE) ->
        {
            CalmSoundsControls(m, dispatch)
            if (m.provider == MusicProvider.APP) AppControls(m, dispatch, compact = true)
        }
        m.provider == MusicProvider.DEVICE_FILE -> FileControls(m)
        m.provider == MusicProvider.APP -> AppControls(m, dispatch, compact = false)
        m.provider == MusicProvider.SPOTIFY -> SpotifyControls(s, bedtime, dispatch)
        else -> T("Music is off. Choose a source in Settings → Music.", Type.label, P().muted)
    }
}

@Composable
private fun CalmSoundsControls(m: MusicPrefs, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    val st by SoundService.state.collectAsState()
    AmbientChips(m.ambient) { a ->
        dispatch(Action.SetMusic(m.copy(ambient = a)))
        if (st.playing) platform.playAmbient(a, m.sleepTimerMin)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(if (st.playing) "■" else "▶", {
            if (st.playing) platform.stopSound() else platform.playAmbient(m.ambient, m.sleepTimerMin)
        }, size = 60.dp, filled = true, tag = "ambientPlay")
        Column {
            T(if (st.playing) st.label else "${m.ambient.icon} ${m.ambient.label}", Type.label, maxLines = 1)
            T(st.endsAt?.let { "Fades out in ${((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)} min" }
                ?: if (m.sleepTimerMin == 0) "Plays until you stop it" else "Fades out after ${m.sleepTimerMin} min", Type.small, p.muted)
        }
    }
}

@Composable
private fun FileControls(m: MusicPrefs) {
    val p = P()
    val platform = LocalPlatform.current
    val st by SoundService.state.collectAsState()
    if (m.fileUri.isBlank()) {
        T("Choose a sound file in Settings → Music.", Type.label, p.muted)
        SecondaryButton("Choose a file", { platform.pickAudioFile() })
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(if (st.playing) "■" else "▶", {
            if (st.playing) platform.stopSound() else platform.playFile(m.fileUri, m.fileName, m.sleepTimerMin)
        }, size = 60.dp, filled = true, tag = "filePlay")
        Column(Modifier.weight(1f)) {
            T("🎵 ${m.fileName}", Type.label, maxLines = 2)
            T(if (m.sleepTimerMin == 0) "Loops until you stop it" else "Fades out after ${m.sleepTimerMin} min", Type.small, p.muted)
        }
    }
}

@Composable
private fun AppControls(m: MusicPrefs, dispatch: Dispatch, compact: Boolean) {
    val p = P()
    val platform = LocalPlatform.current
    if (!compact) T(if (m.appName.isBlank()) "Controls whatever is playing" else "Playing from ${m.appName}", Type.label, p.muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton("⏮", { platform.media(MediaKey.PREVIOUS) }, size = if (compact) 46.dp else 52.dp)
        RoundIconButton("⏯", { platform.media(MediaKey.PLAY_PAUSE) }, size = if (compact) 52.dp else 60.dp, filled = !compact, tag = "playPause")
        RoundIconButton("⏭", { platform.media(MediaKey.NEXT) }, size = if (compact) 46.dp else 52.dp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (m.appPackage.isNotBlank()) QuietButton("Open ${m.appName}", {
            if (!platform.openApp(m.appPackage)) dispatch(Action.ShowMessage("${m.appName} isn't installed on this device."))
        }, tag = "openMusicApp")
        if (m.link.isNotBlank()) QuietButton("My playlist", {
            if (!platform.openLink(m.link.trim(), m.appPackage)) dispatch(Action.ShowMessage("Couldn't open that link."))
        })
    }
}

@Composable
private fun SpotifyControls(s: AppState, bedtime: Boolean, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    val engine = LocalSpotify.current
    val sp = engine?.state?.collectAsState()?.value
    if (engine == null || sp == null || sp.status == SpStatus.NOT_CONFIGURED) {
        T("Add your Spotify Client ID in Settings → Music to see songs and album art. Meanwhile these control whatever is playing:", Type.small, p.muted)
        AppControls(s.data.prefs.music.copy(appPackage = "com.spotify.music", appName = "Spotify"), dispatch, compact = false)
        return
    }
    when (sp.status) {
        SpStatus.CONNECTED -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = sp.art
                if (art != null) androidx.compose.foundation.Image(art.asImageBitmap(), contentDescription = "Album art",
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)))
                else Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(p.cardStrong), contentAlignment = Alignment.Center) { T("♪", Type.title, p.muted) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T(sp.title.ifBlank { "Nothing playing" }, Type.heading, maxLines = 2, modifier = Modifier.testTag("spotifyTitle"))
                    if (sp.artist.isNotBlank()) T(sp.artist, Type.small, p.muted, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("⏮", { engine.previous() }, size = 52.dp)
                RoundIconButton(if (sp.paused) "▶" else "❚❚", { engine.playPause() }, size = 64.dp, filled = true, tag = "playPause")
                RoundIconButton("⏭", { engine.next() }, size = 52.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryButton(if (bedtime) "Sleep sounds…" else "Choose music…", { engine.browse(sleep = bedtime) }, tag = "spotifyBrowse")
                val uri = s.data.prefs.sleepAudioUri.trim()
                if (bedtime && uri.startsWith("spotify:")) QuietButton("My sleep playlist", { engine.play(uri) })
            }
        }
        SpStatus.CONNECTING -> T("Connecting to Spotify…", Type.label, p.muted)
        SpStatus.NOT_INSTALLED -> {
            T("Install Spotify on this device to play music here.", Type.label, p.muted)
            QuietButton("Get Spotify", { platform.openUri("market://details?id=com.spotify.music") })
        }
        else -> {
            T(sp.message ?: "Connect your Spotify to see what's playing and control it here.", Type.label, p.muted)
            PrimaryButton("Connect Spotify", { engine.connect(interactive = true) }, tag = "spotifyConnect")
        }
    }
    if (sp.items.isNotEmpty()) SpotifyBrowser(engine, sp)
}

@Composable
private fun SpotifyBrowser(engine: SpotifyEngine, sp: SpotifyEngine.Ui) {
    val p = P()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { engine.closeBrowse() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 900.dp).padding(20.dp).testTag("spotifyBrowser"),
        containerColor = p.card,
        title = { T(sp.browseTitle ?: "Music", Type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sp.items.forEach { item ->
                    var thumb by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(item.id) { engine.thumbnail(item) { thumb = it } }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).clickable { engine.open(item) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val b = thumb
                        if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                        else Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(p.cardStrong))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(item.title ?: "", Type.label, maxLines = 1)
                            if (!item.subtitle.isNullOrBlank()) T(item.subtitle, Type.small, p.muted, maxLines = 1)
                        }
                        T(if (item.playable) "▶" else "›", Type.heading, p.accent)
                    }
                }
            }
        },
        confirmButton = { SecondaryButton("Close", { engine.closeBrowse() }) },
    )
}
