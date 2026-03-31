package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

class HomeFragmentAiRecommendationsRow(
	private val api: ApiClient,
) : HomeFragmentRow {
	override fun addToRowsAdapter(
		context: Context,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>,
	) {
		// Find the "AI Picks" collection by searching for it
		val collectionId = try {
			runBlocking(Dispatchers.IO) {
				val result by api.itemsApi.getItems(
					includeItemTypes = setOf(BaseItemKind.BOX_SET),
					searchTerm = "AI Picks",
					recursive = true,
					limit = 1,
				)
				result.items.firstOrNull()?.id
			}
		} catch (e: Exception) {
			Timber.w(e, "Failed to find AI Picks collection")
			null
		}

		if (collectionId == null) {
			Timber.i("No 'AI Picks' collection found, skipping AI recommendations row")
			return
		}

		val request = GetItemsRequest(
			parentId = collectionId,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			limit = ITEM_LIMIT,
			sortBy = setOf(ItemSortBy.RANDOM),
			sortOrder = setOf(SortOrder.ASCENDING),
		)

		val title = context.getString(R.string.home_section_ai_recommendations)
		val row = HomeFragmentBrowseRowDefRow(BrowseRowDef(title, request, 0))
		row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	companion object {
		private const val ITEM_LIMIT = 25
	}
}
