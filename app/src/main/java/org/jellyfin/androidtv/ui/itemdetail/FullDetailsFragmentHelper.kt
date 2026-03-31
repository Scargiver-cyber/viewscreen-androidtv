package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.RatingRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.apiclient.getSeriesOverview
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.androidtv.util.sdk.TrailerUtils.getExternalTrailerIntent
import org.jellyfin.androidtv.util.sdk.compat.canResume
import org.jellyfin.androidtv.util.sdk.compat.copyWithUserData
import org.jellyfin.androidtv.util.showIfNotEmpty
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun FullDetailsFragment.deleteItem(
	api: ApiClient,
	item: BaseItemDto,
	dataRefreshService: DataRefreshService,
	navigationRepository: NavigationRepository,
) = lifecycleScope.launch {
	Timber.i("Deleting item ${item.name} (id=${item.id})")

	try {
		withContext(Dispatchers.IO) {
			api.libraryApi.deleteItem(item.id)
		}
	} catch (error: ApiClientException) {
		Timber.e(error, "Failed to delete item ${item.name} (id=${item.id})")
		Toast.makeText(
			context,
			getString(R.string.item_deletion_failed, item.name),
			Toast.LENGTH_LONG
		).show()
		return@launch
	}

	dataRefreshService.lastDeletedItemId = item.id

	if (navigationRepository.canGoBack) navigationRepository.goBack()
	else navigationRepository.navigate(Destinations.home)

	Toast.makeText(context, getString(R.string.item_deleted, item.name), Toast.LENGTH_LONG).show()
}

fun FullDetailsFragment.showDetailsMenu(
	view: View,
	baseItemDto: BaseItemDto,
) = popupMenu(requireContext(), view) {
	// for each button check if it exists (not-null) and is invisible (overflow prevention)
	if (queueButton?.isVisible == false) {
		item(getString(R.string.lbl_add_to_queue)) { addItemToQueue() }
	}

	if (shuffleButton?.isVisible == false) {
		item(getString(R.string.lbl_shuffle_all)) { shufflePlay() }
	}

	if (trailerButton?.isVisible == false) {
		item(getString(R.string.lbl_play_trailers)) { playTrailers() }
	}

	if (favButton?.isVisible == false) {
		val favoriteStringRes = when (baseItemDto.userData?.isFavorite) {
			true -> R.string.lbl_remove_favorite
			else -> R.string.lbl_add_favorite
		}

		item(getString(favoriteStringRes)) { toggleFavorite() }
	}

	if (goToSeriesButton?.isVisible == false) {
		item(getString(R.string.lbl_goto_series)) { gotoSeries() }
	}
}.showIfNotEmpty()

fun FullDetailsFragment.createFakeSeriesTimerBaseItemDto(timer: SeriesTimerInfoDto) = BaseItemDto(
	id = requireNotNull(timer.id).toUUID(),
	type = BaseItemKind.FOLDER,
	mediaType = MediaType.UNKNOWN,
	seriesTimerId = timer.id,
	name = timer.name,
	overview = timer.getSeriesOverview(requireContext()),
)

fun FullDetailsFragment.toggleFavorite() {
	val itemMutationRepository by inject<ItemMutationRepository>()
	val dataRefreshService by inject<DataRefreshService>()

	lifecycleScope.launch {
		val userData = itemMutationRepository.setFavorite(
			item = mBaseItem.id,
			favorite = !(mBaseItem.userData?.isFavorite ?: false)
		)
		mBaseItem = mBaseItem.copyWithUserData(userData)
		favButton.isActivated = userData.isFavorite
		dataRefreshService.lastFavoriteUpdate = Instant.now()
	}
}

fun FullDetailsFragment.togglePlayed() {
	val itemMutationRepository by inject<ItemMutationRepository>()
	val dataRefreshService by inject<DataRefreshService>()

	lifecycleScope.launch {
		val userData = itemMutationRepository.setPlayed(
			item = mBaseItem.id,
			played = !(mBaseItem.userData?.played ?: false)
		)
		mBaseItem = mBaseItem.copyWithUserData(userData)
		mWatchedToggleButton.isActivated = userData.played

		// Adjust resume
		mResumeButton?.apply {
			isVisible = mBaseItem.canResume
		}

		// Force lists to re-fetch
		dataRefreshService.lastPlayback = Instant.now()
		when (mBaseItem.type) {
			BaseItemKind.MOVIE -> dataRefreshService.lastMoviePlayback = Instant.now()
			BaseItemKind.EPISODE -> dataRefreshService.lastTvPlayback = Instant.now()
			else -> Unit
		}

		showMoreButtonIfNeeded()
	}
}

fun FullDetailsFragment.playTrailers() {
	val localTrailerCount = mBaseItem.localTrailerCount ?: 0

	// External trailer
	if (localTrailerCount < 1) try {
		val intent = getExternalTrailerIntent(requireContext(), mBaseItem)
		if (intent != null) startActivity(intent)
	} catch (exception: ActivityNotFoundException) {
		Timber.w(exception, "Unable to open external trailer")
		Toast.makeText(
			requireContext(),
			getString(R.string.no_player_message),
			Toast.LENGTH_LONG
		).show()
	} else lifecycleScope.launch {
		val api by inject<ApiClient>()

		try {
			val trailers = withContext(Dispatchers.IO) {
				api.userLibraryApi.getLocalTrailers(mBaseItem.id).content
			}
			play(trailers, 0, false)
		} catch (exception: ApiClientException) {
			Timber.e(exception, "Error retrieving trailers for playback")
			Toast.makeText(
				requireContext(),
				getString(R.string.msg_video_playback_error),
				Toast.LENGTH_LONG
			).show()
		}
	}
}

fun FullDetailsFragment.getItem(id: UUID, callback: (item: BaseItemDto?) -> Unit) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		val response = try {
			withContext(Dispatchers.IO) {
				api.userLibraryApi.getItem(id).content
			}
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to get item $id")
			null
		}

		callback(response)
	}
}

fun FullDetailsFragment.populatePreviousButton() {
	if (mBaseItem.type != BaseItemKind.EPISODE) return

	val api by inject<ApiClient>()

	lifecycleScope.launch {
		val siblings = withContext(Dispatchers.IO) {
			api.tvShowsApi.getEpisodes(
				seriesId = requireNotNull(mBaseItem.seriesId),
				adjacentTo = mBaseItem.id,
			).content
		}

		val previousItem = siblings.items
			.filterNot { it.id == mBaseItem.id }
			.firstOrNull()
			?.id

		mPrevItemId = previousItem
		mPrevButton.isVisible = previousItem != null

		showMoreButtonIfNeeded()
	}
}

fun FullDetailsFragment.getNextUpEpisode(callback: (BaseItemDto?) -> Unit) {
	lifecycleScope.launch {
		val nextUpEpisode = getNextUpEpisode()
		callback(nextUpEpisode)
	}
}

suspend fun FullDetailsFragment.getNextUpEpisode(): BaseItemDto? {
	val api by inject<ApiClient>()

	try {
		val episodes = withContext(Dispatchers.IO) {
			api.tvShowsApi.getNextUp(
				seriesId = mBaseItem.seriesId ?: mBaseItem.id,
				fields = ItemRepository.itemFields,
				limit = 1,
			).content
		}
		return episodes.items.firstOrNull()
	} catch (err: ApiClientException) {
		Timber.w(err, "Failed to get next up items")
		return null
	}
}

fun FullDetailsFragment.resumePlayback(v: View) {
	if (mBaseItem.type != BaseItemKind.SERIES) {
		val pos = (mBaseItem.userData?.playbackPositionTicks?.ticks
			?: Duration.ZERO) - resumePreroll.milliseconds
		play(mBaseItem, pos.inWholeMilliseconds.toInt(), false)
		return
	}

	lifecycleScope.launch {
		val nextUpEpisode = getNextUpEpisode()
		if (nextUpEpisode == null) {
			Toast.makeText(
				requireContext(),
				getString(R.string.msg_video_playback_error),
				Toast.LENGTH_LONG
			).show()
		} else if (nextUpEpisode.userData?.playbackPositionTicks == 0L) {
			play(nextUpEpisode, 0, false)
		} else {
			showResumeMenu(v, nextUpEpisode)
		}
	}
}

fun FullDetailsFragment.showResumeMenu(
	view: View,
	nextUpEpisode: BaseItemDto
) = popupMenu(requireContext(), view) {
	val pos = (nextUpEpisode.userData?.playbackPositionTicks?.ticks
		?: Duration.ZERO) - resumePreroll.milliseconds
	item(
		getString(
			R.string.lbl_resume_from,
			TimeUtils.formatMillis(pos.inWholeMilliseconds)
		)
	) {
		play(nextUpEpisode, pos.inWholeMilliseconds.toInt(), false)
	}
	item(getString(R.string.lbl_from_beginning)) {
		play(nextUpEpisode, 0, false)
	}
}.showIfNotEmpty()

fun FullDetailsFragment.getLiveTvSeriesTimer(
	id: String,
	callback: (timer: SeriesTimerInfoDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getSeriesTimer(id).content
			}
		}.onSuccess { timer ->
			callback(timer)
		}
	}
}

fun FullDetailsFragment.getLiveTvProgram(
	id: UUID,
	callback: (program: BaseItemDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getProgram(id.toString()).content
			}
		}.onSuccess { program ->
			callback(program)
		}
	}
}

fun FullDetailsFragment.createLiveTvSeriesTimer(
	seriesTimer: SeriesTimerInfoDto,
	callback: () -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.createSeriesTimer(seriesTimer)
			}
		}.onSuccess {
			callback()
		}
	}
}

fun FullDetailsFragment.getLiveTvDefaultTimer(
	id: UUID,
	callback: (seriesTimer: SeriesTimerInfoDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getDefaultTimer(id.toString()).content
			}
		}.onSuccess { seriesTimer ->
			callback(seriesTimer)
		}
	}
}

fun FullDetailsFragment.cancelLiveTvSeriesTimer(
	timerId: String,
	callback: () -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.cancelTimer(timerId)
			}
		}.onSuccess {
			callback()
		}
	}
}

fun FullDetailsFragment.getLiveTvChannel(
	id: UUID,
	callback: (channel: BaseItemDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getChannel(id).content
			}
		}.onSuccess { channel ->
			callback(channel)
		}
	}
}

// --- ViewScreen: Theme Music ---

private val themeMusicPlayers = mutableMapOf<Int, ThemeMusicPlayer>()

fun FullDetailsFragment.startThemeMusic() {
	val item = mBaseItem ?: return
	val api by inject<ApiClient>()
	if (item.type != BaseItemKind.SERIES) return

	val key = System.identityHashCode(this)
	val player = themeMusicPlayers.getOrPut(key) {
		ThemeMusicPlayer(api, lifecycleScope)
	}
	player.play(item.id)
}

fun FullDetailsFragment.stopThemeMusic() {
	val key = System.identityHashCode(this)
	themeMusicPlayers.remove(key)?.stop()
}

// --- ViewScreen: Star Ratings (K3ntas Plugin) ---

private val ratingLabels = arrayOf(
	"", // 0 unused
	"Weak Sauce",
	"Terrible",
	"Bad",
	"Poor",
	"Meh",
	"Fair",
	"Good",
	"Great",
	"Superb",
	"Totally Ninja"
)

fun FullDetailsFragment.showRatingDialog() {
	val ratingRepository by inject<RatingRepository>()
	val goldColor = Color.parseColor("#FFD700")
	val emptyColor = Color.parseColor("#555555")
	val density = resources.displayMetrics.density

	lifecycleScope.launch {
		val currentRating = ratingRepository.getUserRating(mBaseItem.id)
		var selectedRating = currentRating ?: 5

		// Build the dialog layout programmatically
		val container = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			gravity = Gravity.CENTER_HORIZONTAL
			setPadding((24 * density).toInt(), (20 * density).toInt(), (24 * density).toInt(), (16 * density).toInt())
			setBackgroundColor(Color.parseColor("#1A1A1A"))
		}

		// Title
		val titleView = TextView(requireContext()).apply {
			text = "Rate This"
			setTextColor(Color.WHITE)
			textSize = 20f
			typeface = Typeface.DEFAULT_BOLD
			gravity = Gravity.CENTER
		}
		container.addView(titleView)

		// Star row
		val starRow = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER
			val lp = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			lp.topMargin = (16 * density).toInt()
			layoutParams = lp
		}
		val starSize = (32 * density).toInt()
		val starViews = mutableListOf<ImageView>()
		for (i in 1..10) {
			val star = ImageView(requireContext()).apply {
				setImageResource(R.drawable.ic_star)
				val lp = LinearLayout.LayoutParams(starSize, starSize)
				lp.marginEnd = (2 * density).toInt()
				layoutParams = lp
			}
			starViews.add(star)
			starRow.addView(star)
		}
		container.addView(starRow)

		// Rating number
		val numberView = TextView(requireContext()).apply {
			textSize = 26f
			typeface = Typeface.DEFAULT_BOLD
			setTextColor(goldColor)
			gravity = Gravity.CENTER
			val lp = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			lp.topMargin = (12 * density).toInt()
			layoutParams = lp
		}
		container.addView(numberView)

		// Label
		val labelView = TextView(requireContext()).apply {
			textSize = 15f
			setTextColor(Color.parseColor("#AAAAAA"))
			gravity = Gravity.CENTER
			val lp = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			lp.topMargin = (4 * density).toInt()
			layoutParams = lp
		}
		container.addView(labelView)

		// Instructions
		val instrView = TextView(requireContext()).apply {
			text = if (currentRating != null)
				"\u25C0 \u25B6 to change  \u2022  OK to confirm  \u2022  DEL to clear"
			else
				"\u25C0 \u25B6 to change  \u2022  OK to confirm"
			textSize = 11f
			setTextColor(Color.parseColor("#777777"))
			gravity = Gravity.CENTER
			val lp = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			lp.topMargin = (16 * density).toInt()
			layoutParams = lp
		}
		container.addView(instrView)

		fun updateStars() {
			for (i in starViews.indices) {
				starViews[i].setColorFilter(if (i < selectedRating) goldColor else emptyColor)
			}
			numberView.text = "$selectedRating / 10"
			labelView.text = ratingLabels.getOrElse(selectedRating) { "" }
		}
		updateStars()

		val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Jellyfin_Dialog)
			.setView(container)
			.create()

		dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

		// Handle D-pad navigation
		container.isFocusable = true
		container.isFocusableInTouchMode = true
		container.setOnKeyListener { _, keyCode, event ->
			if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
			when (keyCode) {
				android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
					if (selectedRating > 1) {
						selectedRating--
						updateStars()
					}
					true
				}
				android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_UP -> {
					if (selectedRating < 10) {
						selectedRating++
						updateStars()
					}
					true
				}
				android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
					dialog.dismiss()
					submitRating(selectedRating)
					true
				}
				android.view.KeyEvent.KEYCODE_FORWARD_DEL, android.view.KeyEvent.KEYCODE_DEL -> {
					if (currentRating != null) {
						dialog.dismiss()
						clearRating()
					}
					true
				}
				else -> false
			}
		}

		dialog.show()
		container.requestFocus()
	}
}

fun FullDetailsFragment.submitRating(rating: Int) {
	val ratingRepository by inject<RatingRepository>()

	lifecycleScope.launch {
		ratingRepository.setRating(mBaseItem.id, rating)
		rateButton?.setLabel(getString(R.string.lbl_rate_value, rating))
		Toast.makeText(requireContext(), "Rated $rating/10", Toast.LENGTH_SHORT).show()
	}
}

fun FullDetailsFragment.clearRating() {
	val ratingRepository by inject<RatingRepository>()

	lifecycleScope.launch {
		ratingRepository.clearRating(mBaseItem.id)
		rateButton?.setLabel(getString(R.string.lbl_rate))
		Toast.makeText(requireContext(), "Rating cleared", Toast.LENGTH_SHORT).show()
	}
}

fun FullDetailsFragment.loadUserRating() {
	val ratingRepository by inject<RatingRepository>()

	lifecycleScope.launch {
		val rating = ratingRepository.getUserRating(mBaseItem.id)
		if (rating != null) {
			rateButton?.setLabel(getString(R.string.lbl_rate_value, rating))
		}
	}
}
