package org.jellyfin.androidtv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.KeyEvent
import org.jellyfin.androidtv.R

class NavSoundPlayer(context: Context) {
	private val soundPool = SoundPool.Builder()
		.setMaxStreams(2)
		.setAudioAttributes(
			AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.build()
		)
		.build()

	private val clickSoundId = soundPool.load(context, R.raw.nav_click, 1)

	fun onKeyEvent(keyCode: Int, action: Int) {
		if (action != KeyEvent.ACTION_DOWN) return

		when (keyCode) {
			KeyEvent.KEYCODE_DPAD_UP,
			KeyEvent.KEYCODE_DPAD_DOWN,
			KeyEvent.KEYCODE_DPAD_LEFT,
			KeyEvent.KEYCODE_DPAD_RIGHT -> {
				soundPool.play(clickSoundId, 0.15f, 0.15f, 1, 0, 1f)
			}
		}
	}

	fun release() {
		soundPool.release()
	}
}
