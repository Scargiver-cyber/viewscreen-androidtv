package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

enum class MainToolbarActiveButton {
	Home,
	Search,

	None,
}

@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
) {
	val focusRequester = remember { FocusRequester() }
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		start = {
			ToolbarButtons(
				modifier = Modifier.focusRequester(focusRequester)
			) {
				NowPlayingComposable(
					onFocusableChange = {},
				)
			}
		},
		center = {},
		end = {
			ToolbarButtons {
				IconButton(
					onClick = { settingsViewModel.show() },
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				ToolbarClock()
			}
		}
	)
}
