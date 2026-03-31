package org.jellyfin.androidtv.data.repository

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RatingCache(
	private val ratingRepository: RatingRepository,
) {
	// Sentinel: UNRATED means "fetched, no rating". Absence from map means "not yet fetched".
	private val cache = ConcurrentHashMap<UUID, Int>()

	suspend fun getRating(itemId: UUID): Int? {
		val cached = cache[itemId]
		if (cached != null) return if (cached == UNRATED) null else cached

		val rating = ratingRepository.getUserRating(itemId)
		cache[itemId] = rating ?: UNRATED
		return rating
	}

	fun invalidate(itemId: UUID) {
		cache.remove(itemId)
	}

	private companion object {
		const val UNRATED = -1
	}
}
