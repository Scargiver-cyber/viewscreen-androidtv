package org.jellyfin.androidtv.ui.shared.sidebar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.ButtonColors
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.shared.toolbar.ToolbarClock
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

enum class SidebarActiveItem {
	Home,
	Movies,
	TvShows,
	Genres,
	Search,
	None,
}

data class SidebarIcons(
	val home: Int = R.drawable.ic_house,
	val movies: Int = R.drawable.ic_movie,
	val tvShows: Int = R.drawable.ic_tv,
	val genres: Int = R.drawable.ic_masks,
	val search: Int = R.drawable.ic_search,
	val settings: Int = R.drawable.ic_settings,
)

fun iconsForUser(userName: String?): SidebarIcons = when {
	userName.equals("Daddy", ignoreCase = true) -> SidebarIcons(
		home = R.drawable.ic_delta,          // Starfleet chevron
		movies = R.drawable.ic_clapperboard, // director's board
		genres = R.drawable.ic_flask,        // science/trek vibe
	)
	userName.equals("Momma", ignoreCase = true) -> SidebarIcons(
		home = R.drawable.ic_heart,          // warm & cozy
		movies = R.drawable.ic_clover,       // four leaf clover
		genres = R.drawable.ic_skull,        // horror themed
	)
	userName.equals("Mad Maddie", ignoreCase = true) -> SidebarIcons(
		home = R.drawable.ic_paw,            // wolf/dragon paw
		movies = R.drawable.ic_clapperboard,
	)
	userName.equals("Lil Sweet", ignoreCase = true) -> SidebarIcons(
		home = R.drawable.ic_blood_drop,     // blood drop
	)
	else -> SidebarIcons()
}

@Composable
fun SidebarNavigation(
	activeItem: SidebarActiveItem = SidebarActiveItem.Home,
	focusRequester: FocusRequester = remember { FocusRequester() },
	downFocusTarget: FocusRequester? = null,
) {
	val navigationRepository = koinInject<NavigationRepository>()
	val userRepository = koinInject<UserRepository>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val sessionRepository = koinInject<SessionRepository>()
	val mediaManager = koinInject<MediaManager>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val api = koinInject<ApiClient>()
	val activity = LocalActivity.current

	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	// Fetch library views to find Movies and TV Shows libraries
	var moviesLibrary by remember { mutableStateOf<BaseItemDto?>(null) }
	var tvLibrary by remember { mutableStateOf<BaseItemDto?>(null) }

	LaunchedEffect(Unit) {
		val views = userViewsRepository.views.firstOrNull() ?: return@LaunchedEffect
		moviesLibrary = views.firstOrNull { it.collectionType == CollectionType.MOVIES }
		tvLibrary = views.firstOrNull { it.collectionType == CollectionType.TVSHOWS }
	}

	// Genre picker dialog state
	var showGenreDialog by remember { mutableStateOf(false) }
	var genreList by remember { mutableStateOf<List<String>>(emptyList()) }
	val scope = rememberCoroutineScope()

	val icons = iconsForUser(currentUser?.name)
	val activeColors = ButtonDefaults.colors(
		containerColor = JellyfinTheme.colorScheme.buttonActive,
		contentColor = JellyfinTheme.colorScheme.onButtonActive,
	)
	val navButtonShape = JellyfinTheme.shapes.medium

	Column(
		modifier = Modifier
			.fillMaxHeight()
			.width(80.dp)
			.background(JellyfinTheme.colorScheme.sidebarBackground)
			.padding(horizontal = 8.dp, vertical = 16.dp)
			.focusRequester(focusRequester)
			.onPreviewKeyEvent { keyEvent ->
				if (keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown) {
					downFocusTarget?.requestFocus()
					downFocusTarget != null
				} else {
					false
				}
			}
			.focusGroup(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// User avatar
		val userImagePainter = rememberAsyncImagePainter(userImage)
		val userImageState by userImagePainter.state.collectAsState()
		val userImageVisible = userImageState is AsyncImagePainter.State.Success

		IconButton(
			onClick = {
				mediaManager.clearAudioQueue()
				sessionRepository.destroyCurrentSession()
				activity?.startActivity(ActivityDestinations.startup(activity))
				activity?.finishAfterTransition()
			},
			contentPadding = if (userImageVisible) PaddingValues(3.dp) else IconButtonDefaults.ContentPadding,
		) {
			ProfilePicture(
				url = userImage,
				contentDescription = stringResource(R.string.lbl_switch_user),
				modifier = Modifier
					.aspectRatio(1f)
					.clip(IconButtonDefaults.Shape)
			)
		}

		Spacer(modifier = Modifier.height(4.dp))

		// Home
		NavItem(
			iconRes = icons.home,
			label = stringResource(R.string.lbl_home),
			isActive = activeItem == SidebarActiveItem.Home,
			activeColors = activeColors,
			buttonShape = navButtonShape,
			onClick = {
				if (activeItem != SidebarActiveItem.Home) {
					navigationRepository.navigate(Destinations.home, replace = true)
				}
			}
		)

		// Movies
		NavItem(
			iconRes = icons.movies,
			label = stringResource(R.string.lbl_movies),
			isActive = activeItem == SidebarActiveItem.Movies,
			activeColors = activeColors,
			buttonShape = navButtonShape,
			onClick = {
				moviesLibrary?.let { lib ->
					navigationRepository.navigate(Destinations.librarySmartScreen(lib))
				}
			}
		)

		// TV Shows
		NavItem(
			iconRes = icons.tvShows,
			label = stringResource(R.string.lbl_tv_shows),
			isActive = activeItem == SidebarActiveItem.TvShows,
			activeColors = activeColors,
			buttonShape = navButtonShape,
			onClick = {
				tvLibrary?.let { lib ->
					navigationRepository.navigate(Destinations.librarySmartScreen(lib))
				}
			}
		)

		// Genres
		NavItem(
			iconRes = icons.genres,
			label = stringResource(R.string.lbl_genres),
			isActive = activeItem == SidebarActiveItem.Genres,
			activeColors = activeColors,
			buttonShape = navButtonShape,
			onClick = {
				moviesLibrary?.let { lib ->
					scope.launch {
						val response = withContext(Dispatchers.IO) {
							api.genresApi.getGenres(
								parentId = lib.id,
								sortBy = setOf(ItemSortBy.SORT_NAME),
							).content
						}
						genreList = response.items.mapNotNull { it.name }
						showGenreDialog = true
					}
				}
			}
		)

		// Search
		NavItem(
			iconRes = icons.search,
			label = stringResource(R.string.lbl_search),
			isActive = activeItem == SidebarActiveItem.Search,
			activeColors = activeColors,
			buttonShape = navButtonShape,
			onClick = {
				navigationRepository.navigate(Destinations.search())
			}
		)

		Spacer(modifier = Modifier.weight(1f))

		// Settings
		NavItem(
			iconRes = icons.settings,
			label = stringResource(R.string.lbl_settings),
			isActive = false,
			activeColors = activeColors,
			buttonShape = navButtonShape,
			onClick = { settingsViewModel.show() }
		)

		// Clock
		ToolbarClock()
	}

	// Genre picker dialog
	if (showGenreDialog) {
		Dialog(onDismissRequest = { showGenreDialog = false }) {
			Column(
				modifier = Modifier
					.width(300.dp)
					.background(
						Color(0xFF1A1A2E),
						shape = RoundedCornerShape(12.dp)
					)
					.padding(16.dp),
			) {
				Text(
					text = "Pick a Genre",
					fontSize = 18.sp,
					color = Color.White,
					modifier = Modifier.padding(bottom = 12.dp),
				)
				LazyColumn(
					modifier = Modifier.heightIn(max = 400.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					items(genreList) { genre ->
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.clickable {
									showGenreDialog = false
									moviesLibrary?.let { lib ->
										navigationRepository.navigate(
											Destinations.libraryBrowserByGenre(lib, "Movie", genre)
										)
									}
								}
								.padding(vertical = 10.dp, horizontal = 8.dp),
						) {
							Text(
								text = genre,
								color = Color.White,
								fontSize = 16.sp,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun NavItem(
	iconRes: Int,
	label: String,
	isActive: Boolean,
	activeColors: ButtonColors,
	buttonShape: Shape = ButtonDefaults.Shape,
	onClick: () -> Unit,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		IconButton(
			onClick = onClick,
			colors = if (isActive) activeColors else ButtonDefaults.colors(),
			shape = buttonShape,
			modifier = Modifier.size(48.dp),
			contentPadding = PaddingValues(0.dp),
		) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(iconRes),
					contentDescription = label,
					modifier = Modifier.size(22.dp),
				)
			}
		}
		Text(
			text = label,
			fontSize = 9.sp,
			color = if (isActive) Color.White else Color(0xFFAAAAAA),
		)
	}
}
