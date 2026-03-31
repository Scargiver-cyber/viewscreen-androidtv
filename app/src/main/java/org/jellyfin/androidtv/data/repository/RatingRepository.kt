package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.UUID

interface RatingRepository {
	suspend fun getUserRating(itemId: UUID): Int?
	suspend fun setRating(itemId: UUID, rating: Int)
	suspend fun clearRating(itemId: UUID)
}

class RatingRepositoryImpl(
	private val api: ApiClient,
) : RatingRepository {
	private val httpClient = OkHttpClient()

	private fun buildUrl(path: String): String {
		val baseUrl = api.baseUrl?.trimEnd('/')
		return "$baseUrl$path"
	}

	private fun buildAuthHeader(): String {
		return "MediaBrowser Token=\"${api.accessToken}\""
	}

	override suspend fun getUserRating(itemId: UUID): Int? = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder()
				.url(buildUrl("/Ratings/Items/$itemId/UserRating"))
				.header("Authorization", buildAuthHeader())
				.get()
				.build()

			val response = httpClient.newCall(request).execute()
			if (response.isSuccessful) {
				val body = response.body?.string()
				// K3ntas returns a JSON number or object with rating field
				body?.trim()?.toIntOrNull()
					?: body?.let {
						// Try parsing as JSON object: {"rating": 7}
						val match = Regex("\"rating\"\\s*:\\s*(\\d+)").find(it)
						match?.groupValues?.get(1)?.toIntOrNull()
					}
			} else if (response.code == 404) {
				null // No rating set
			} else {
				Timber.w("Failed to get rating for $itemId: ${response.code}")
				null
			}
		} catch (e: Exception) {
			Timber.e(e, "Error getting rating for $itemId")
			null
		}
	}

	override suspend fun setRating(itemId: UUID, rating: Int) = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder()
				.url(buildUrl("/Ratings/Items/$itemId/Rating?rating=$rating"))
				.header("Authorization", buildAuthHeader())
				.post("".toRequestBody("application/json".toMediaType()))
				.build()

			val response = httpClient.newCall(request).execute()
			if (!response.isSuccessful) {
				Timber.w("Failed to set rating for $itemId: ${response.code}")
			}
		} catch (e: Exception) {
			Timber.e(e, "Error setting rating for $itemId")
		}
	}

	override suspend fun clearRating(itemId: UUID) = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder()
				.url(buildUrl("/Ratings/Items/$itemId/Rating"))
				.header("Authorization", buildAuthHeader())
				.delete()
				.build()

			val response = httpClient.newCall(request).execute()
			if (!response.isSuccessful) {
				Timber.w("Failed to clear rating for $itemId: ${response.code}")
			}
		} catch (e: Exception) {
			Timber.e(e, "Error clearing rating for $itemId")
		}
	}
}
