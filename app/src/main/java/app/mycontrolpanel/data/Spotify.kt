package app.mycontrolpanel.data

import android.content.Context
import android.graphics.Bitmap
import app.mycontrolpanel.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.ContentApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Spotify, embedded through Spotify's official App Remote SDK: the Spotify app (installed and logged in)
 * does the playing; we show what's on, album art, controls, and let you pick something to play.
 * Playback control needs Spotify Premium. Nothing is scraped; no password ever touches this app.
 */
class SpotifyEngine(private val ctx: Context) {

    enum class Status { NOT_CONFIGURED, NOT_INSTALLED, DISCONNECTED, CONNECTING, CONNECTED, NEEDS_LOGIN, NOT_ALLOWED, ERROR }

    data class Ui(
        val status: Status,
        val title: String = "",
        val artist: String = "",
        val paused: Boolean = true,
        val art: Bitmap? = null,
        val trackUri: String? = null,
        val browseTitle: String? = null,
        val items: List<ListItem> = emptyList(),
        val message: String? = null,
    )

    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val prefs = ctx.getSharedPreferences("spotify", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(Ui(if (clientId.isBlank()) Status.NOT_CONFIGURED else Status.DISCONNECTED))
    val state: StateFlow<Ui> get() = _state

    private var remote: SpotifyAppRemote? = null
    private var playerSub: Subscription<PlayerState>? = null
    private var lastImage: String? = null

    val configured get() = clientId.isNotBlank()
    /** True once the user has connected at least once — then we reconnect silently on wake. */
    val linked get() = prefs.getBoolean("linked", false)

    private fun set(f: (Ui) -> Ui) { _state.value = f(_state.value) }

    fun connect(interactive: Boolean) {
        if (!configured) return
        if (!SpotifyAppRemote.isSpotifyInstalled(ctx)) { set { it.copy(status = Status.NOT_INSTALLED) }; return }
        if (remote?.isConnected == true) return
        set { it.copy(status = Status.CONNECTING, message = null) }
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri("mycontrolpanel://spotify-callback")
            .showAuthView(interactive)
            .build()
        SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
            override fun onConnected(r: SpotifyAppRemote) {
                remote = r
                prefs.edit().putBoolean("linked", true).apply()
                set { it.copy(status = Status.CONNECTED, message = null) }
                playerSub = r.playerApi.subscribeToPlayerState().setEventCallback { ps -> onPlayer(r, ps) }
            }
            override fun onFailure(t: Throwable) {
                remote = null
                val (st, msg) = when (t) {
                    is CouldNotFindSpotifyApp -> Status.NOT_INSTALLED to "Install Spotify to use this."
                    is NotLoggedInException -> Status.NEEDS_LOGIN to "Open Spotify and log in, then tap Connect."
                    is UserNotAuthorizedException -> Status.NOT_ALLOWED to
                        "Spotify hasn't allowed this account yet. In the Spotify developer dashboard, add it under User Management."
                    else -> (if (interactive) Status.ERROR else Status.DISCONNECTED) to (t.message ?: "Couldn't connect to Spotify.")
                }
                set { it.copy(status = st, message = msg) }
            }
        })
    }

    fun disconnect() {
        runCatching { playerSub?.cancel() }
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null; playerSub = null
        if (_state.value.status == Status.CONNECTED || _state.value.status == Status.CONNECTING) set { it.copy(status = Status.DISCONNECTED) }
    }

    private fun onPlayer(r: SpotifyAppRemote, ps: PlayerState) {
        val t = ps.track
        set { it.copy(title = t?.name ?: "", artist = t?.artist?.name ?: "", paused = ps.isPaused, trackUri = t?.uri) }
        val img = t?.imageUri
        if (img != null && img.raw != lastImage) {
            lastImage = img.raw
            r.imagesApi.getImage(img, Image.Dimension.MEDIUM).setResultCallback { bmp -> set { it.copy(art = bmp) } }
        }
    }

    private inline fun withRemote(f: (SpotifyAppRemote) -> Unit) {
        val r = remote
        if (r == null || !r.isConnected) { connect(interactive = true); return }
        f(r)
    }

    fun playPause() = withRemote { r -> if (_state.value.paused) r.playerApi.resume() else r.playerApi.pause() }
    fun next() = withRemote { it.playerApi.skipNext() }
    fun previous() = withRemote { it.playerApi.skipPrevious() }
    fun play(uri: String) = withRemote { it.playerApi.play(uri) }

    /** Spotify's own shelves (Recently played, Your playlists, …). [sleep] asks for its sleep shelf. */
    fun browse(sleep: Boolean) = withRemote { r ->
        r.contentApi.getRecommendedContentItems(if (sleep) ContentApi.ContentType.SLEEP else ContentApi.ContentType.DEFAULT)
            .setResultCallback { list -> set { it.copy(browseTitle = if (sleep) "Sleep" else "Your music", items = list.items?.toList() ?: emptyList()) } }
    }

    fun open(item: ListItem) = withRemote { r ->
        if (item.playable) { r.contentApi.playContentItem(item); set { it.copy(items = emptyList(), browseTitle = null) } }
        else if (item.hasChildren) r.contentApi.getChildrenOfItem(item, 30, 0)
            .setResultCallback { list -> set { it.copy(browseTitle = item.title, items = list.items?.toList() ?: emptyList()) } }
    }

    fun closeBrowse() = set { it.copy(items = emptyList(), browseTitle = null) }

    /** Small square thumbnail for a browse item. */
    fun thumbnail(item: ListItem, onBitmap: (Bitmap) -> Unit) {
        val r = remote ?: return
        val img = item.imageUri ?: return
        r.imagesApi.getImage(img, Image.Dimension.THUMBNAIL).setResultCallback { onBitmap(it) }
    }
}
