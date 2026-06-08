package top.ekiz.whitenoise.service

import android.content.ComponentName
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NoiseTileService : TileService() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateTileState(isPlaying)
            }
        }

    override fun onStartListening() {
        super.onStartListening()
        val sessionToken = SessionToken(this, ComponentName(this, NoiseService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    mediaController?.addListener(playerListener)
                    updateTileState(mediaController?.isPlaying == true)
                } catch (e: Exception) {
                    Log.e("NoiseTileService", "Failed to connect to MediaController", e)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStopListening() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val controller = mediaController
        if (controller != null) {
            togglePlay(controller)
        } else {
            controllerFuture?.addListener(
                {
                    val futureController =
                        try {
                            controllerFuture?.get()
                        } catch (e: Exception) {
                            null
                        }
                    if (futureController != null) {
                        togglePlay(futureController)
                    }
                },
                MoreExecutors.directExecutor()
            )
        }
    }

    private fun togglePlay(controller: MediaController) {
        val isPlaying = controller.isPlaying
        if (isPlaying) {
            controller.sendCustomCommand(
                SessionCommand("PAUSE_WITH_FADE", Bundle.EMPTY),
                Bundle.EMPTY
            )
        } else {
            controller.sendCustomCommand(
                SessionCommand("PLAY_WITH_FADE", Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    private fun updateTileState(isPlaying: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "白噪音"
        tile.updateTile()
    }
}
