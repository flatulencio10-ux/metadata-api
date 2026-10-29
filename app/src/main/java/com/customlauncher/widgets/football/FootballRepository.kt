package com.customlauncher.widgets.football

import com.customlauncher.core.json.CompactJson
import com.customlauncher.data.db.CacheDao
import com.customlauncher.data.db.CachedResponse
import com.customlauncher.data.settings.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

sealed interface RefreshResult {
    data object Ok : RefreshResult
    data object Fresh : RefreshResult
    data object MissingKey : RefreshResult
    data class Failed(val message: String) : RefreshResult
}

/**
 * football-data.org v4 client with a Room-backed cache. Network is only touched by [refresh];
 * UI reads go through [observe], so the widget works offline with the last good data.
 */
class FootballRepository(
    private val dao: CacheDao,
    private val secureStore: SecureStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val limiter = RateLimiter(maxRequests = 10, windowMs = 60_000)
    private val refreshMutex = Mutex()

    fun observe(code: String): Flow<FootballData?> = combine(
        dao.observe(key(code, "finished")),
        dao.observe(key(code, "upcoming")),
        dao.observe(key(code, "standings")),
    ) { finished, upcoming, standings ->
        if (finished == null && upcoming == null && standings == null) return@combine null
        val f = finished?.let { parse(it.body, FdMatchesResponse.serializer()) }
        val u = upcoming?.let { parse(it.body, FdMatchesResponse.serializer()) }
        val s = standings?.let { parse(it.body, FdStandingsResponse.serializer()) }
        FootballData(
            competition = f?.competition ?: u?.competition ?: s?.competition,
            recent = f?.matches.orEmpty().sortedByDescending { it.utcDate },
            upcoming = u?.matches.orEmpty()
                .filter { it.status in UPCOMING_STATUSES }
                .sortedBy { it.utcDate },
            standings = s?.standings.orEmpty().filter { it.type == null || it.type == "TOTAL" },
            lastUpdated = listOfNotNull(finished?.fetchedAt, upcoming?.fetchedAt, standings?.fetchedAt).maxOrNull(),
        )
    }

    /**
     * Refreshes one competition. Skips the network when the cache is younger than
     * [maxAgeMs] unless [force]. Requests are serialised and rate limited.
     */
    suspend fun refresh(code: String, force: Boolean = false, maxAgeMs: Long = REFRESH_INTERVAL_MS): RefreshResult {
        return refreshMutex.withLock {
            val apiKey = secureStore.get(SecureStore.FOOTBALL_API_KEY) ?: return RefreshResult.MissingKey
            val newest = dao.get(key(code, "finished"))?.fetchedAt ?: 0L
            if (!force && System.currentTimeMillis() - newest < maxAgeMs) return RefreshResult.Fresh

            val today = LocalDate.now(ZoneOffset.UTC)
            val requests = listOf(
                "finished" to "competitions/$code/matches?status=FINISHED",
                // Upcoming: SCHEDULED and TIMED both mean "not played yet", so filter client side.
                "upcoming" to "competitions/$code/matches?dateFrom=$today&dateTo=${today.plusDays(60)}",
                "standings" to "competitions/$code/standings",
            )
            for ((suffix, path) in requests) {
                when (val r = get(path, apiKey)) {
                    is HttpResult.Body -> dao.put(CachedResponse(key(code, suffix), r.body, System.currentTimeMillis()))
                    is HttpResult.Error -> {
                        // Standings do not exist for some cup stages; keep going for the rest.
                        if (suffix == "standings" && r.code == 404) continue
                        return RefreshResult.Failed(r.message)
                    }
                }
            }
            RefreshResult.Ok
        }
    }

    private sealed interface HttpResult {
        data class Body(val body: String) : HttpResult
        data class Error(val code: Int, val message: String) : HttpResult
    }

    private suspend fun get(path: String, apiKey: String): HttpResult {
        limiter.acquire()
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/$path")
                .header("X-Auth-Token", apiKey)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> HttpResult.Body(body)
                        response.code == 429 -> {
                            val resetSeconds = response.header("X-RequestCounter-Reset")?.toLongOrNull() ?: 60L
                            limiter.backOff(resetSeconds * 1000)
                            HttpResult.Error(429, "Rate limit reached, retrying later")
                        }
                        response.code == 400 || response.code == 403 ->
                            HttpResult.Error(response.code, "Access denied: check the API key and that the competition is on your plan")
                        else -> HttpResult.Error(response.code, "HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                HttpResult.Error(-1, "Offline")
            }
        }
    }

    private fun <T> parse(body: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { CompactJson.decodeFromString(serializer, body) }.getOrNull()

    private fun key(code: String, suffix: String) = "football:$code:$suffix"

    companion object {
        const val BASE_URL = "https://api.football-data.org/v4"
        const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private val UPCOMING_STATUSES = setOf("SCHEDULED", "TIMED", "IN_PLAY", "PAUSED", "LIVE")
    }
}
