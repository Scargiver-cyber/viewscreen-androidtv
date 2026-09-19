package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.colorSchemeForUser
import org.jellyfin.androidtv.ui.base.shapesForUser
import org.jellyfin.androidtv.ui.shared.sidebar.SidebarActiveItem
import org.jellyfin.androidtv.ui.shared.sidebar.SidebarNavigation
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		val navBarFocusRequester = remember { FocusRequester() }
		LaunchedEffect(rowsFocusRequester) { rowsFocusRequester.requestFocus() }

		val userRepository = koinInject<UserRepository>()
		val currentUser by userRepository.currentUser.collectAsState()
		val userName = currentUser?.name
		val userScheme = colorSchemeForUser(userName)
		val userShapes = shapesForUser(userName)

		JellyfinTheme(colorScheme = userScheme, shapes = userShapes) {
			Row(modifier = Modifier.fillMaxSize()) {
				SidebarNavigation(
					activeItem = SidebarActiveItem.Home,
					focusRequester = navBarFocusRequester,
					downFocusTarget = rowsFocusRequester,
				)

				var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
				AndroidFragment<HomeRowsFragment>(
					modifier = Modifier
						.focusGroup()
						.focusRequester(rowsFocusRequester)
						.focusProperties {
							onExit = {
								val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
								if (requestedFocusDirection == FocusDirection.Left && isFirstRowSelected) {
									rowsSupportFragment?.selectedPosition = 0
									rowsSupportFragment?.verticalGridView?.clearFocus()
								} else {
									cancelFocusChange()
								}
							}
						}
						.weight(1f)
						.fillMaxSize(),
					onUpdate = { fragment ->
						rowsSupportFragment = fragment
					}
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}
