package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

/**
 * Home screen row displaying all TV series sorted alphabetically.
 * Fetches Series items via the Jellyfin /Items API with filters to exclude
 * unaired and future-release content.
 */
class HomeFragmentTvShowsRow : HomeFragmentRow {
	override fun addToRowsAdapter(
		context: Context,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>,
	) {
		val request = GetItemsRequest(
			includeItemTypes = setOf(BaseItemKind.SERIES),
			recursive = true,
			sortBy = setOf(ItemSortBy.SORT_NAME),
			sortOrder = setOf(SortOrder.ASCENDING),
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			limit = ITEM_LIMIT,
		)

		val title = context.getString(R.string.home_section_tv_shows)
		val row = HomeFragmentBrowseRowDefRow(
			BrowseRowDef(title, request, 0, arrayOf(ChangeTriggerType.LibraryUpdated))
		)
		row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	companion object {
		private const val ITEM_LIMIT = 50
	}
}
