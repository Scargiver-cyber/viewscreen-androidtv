package org.jellyfin.androidtv.ui.itemdetail

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import timber.log.Timber
import java.util.UUID

class ThemeMusicPlayer(
	private val api: ApiClient,
	private val scope: CoroutineScope,
) {
	private var mediaPlayer: MediaPlayer? = null
	private var fadeJob: Job? = null
	private var isMuted = false

	private companion object {
		const val MAX_VOLUME = 0.3f
		const val FADE_IN_DURATION_MS = 2000L
		const val FADE_OUT_DURATION_MS = 1000L
		const val FADE_STEPS = 20
		const val START_DELAY_MS = 1000L
	}

	fun play(itemId: UUID) {
		scope.launch {
			try {
				val result = withContext(Dispatchers.IO) {
					api.libraryApi.getThemeSongs(itemId).content
				}
				val songs = result.items
				if (songs.isEmpty()) {
					Timber.d("No theme songs for item $itemId")
					return@launch
				}

				val song = songs.first()
				val baseUrl = api.baseUrl?.trimEnd('/')
				val streamUrl = "$baseUrl/Audio/${song.id}/universal?api_key=${api.accessToken}"

				Timber.i("Playing theme song for item $itemId")

				withContext(Dispatchers.Main) {
					stop()
					delay(START_DELAY_MS)

					mediaPlayer = MediaPlayer().apply {
						setDataSource(streamUrl)
						isLooping = true
						setVolume(0f, 0f)
						setOnPreparedListener {
							start()
							fadeIn()
						}
						setOnErrorListener { _, what, extra ->
							Timber.w("Theme music error: what=$what extra=$extra")
							true
						}
						prepareAsync()
					}
				}
			} catch (e: Exception) {
				Timber.w(e, "Failed to play theme music for $itemId")
			}
		}
	}

	fun stop() {
		fadeJob?.cancel()
		fadeJob = null
		mediaPlayer?.let { player ->
			if (player.isPlaying) {
				fadeOut {
					player.stop()
					player.release()
				}
			} else {
				player.release()
			}
		}
		mediaPlayer = null
	}

	fun setMuted(muted: Boolean) {
		isMuted = muted
		mediaPlayer?.let { player ->
			if (muted) {
				player.setVolume(0f, 0f)
			} else {
				player.setVolume(MAX_VOLUME, MAX_VOLUME)
			}
		}
	}

	private fun fadeIn() {
		if (isMuted) return
		fadeJob?.cancel()
		fadeJob = scope.launch(Dispatchers.Main) {
			val player = mediaPlayer ?: return@launch
			val stepDelay = FADE_IN_DURATION_MS / FADE_STEPS
			for (step in 1..FADE_STEPS) {
				val volume = (step.toFloat() / FADE_STEPS) * MAX_VOLUME
				player.setVolume(volume, volume)
				delay(stepDelay)
			}
		}
	}

	private fun fadeOut(onComplete: () -> Unit) {
		fadeJob?.cancel()
		fadeJob = scope.launch(Dispatchers.Main) {
			val player = mediaPlayer ?: run { onComplete(); return@launch }
			val currentVolume = if (isMuted) 0f else MAX_VOLUME
			val stepDelay = FADE_OUT_DURATION_MS / FADE_STEPS
			for (step in 1..FADE_STEPS) {
				val volume = currentVolume * (1f - step.toFloat() / FADE_STEPS)
				try {
					player.setVolume(volume, volume)
				} catch (e: IllegalStateException) {
					break
				}
				delay(stepDelay)
			}
			onComplete()
		}
	}
}
