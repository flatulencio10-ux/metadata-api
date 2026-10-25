package com.customlauncher.widgets.football

import kotlinx.serialization.Serializable

// Subset of the football-data.org v4 schema; unknown fields are ignored.

@Serializable
data class FdTeam(
    val id: Long? = null,
    val name: String? = null,
    val shortName: String? = null,
    val tla: String? = null,
    val crest: String? = null,
) {
    val displayName: String get() = shortName ?: name ?: tla ?: "?"

    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return q.equals(id?.toString(), true) || q.equals(tla, true) ||
            name?.contains(q, true) == true || shortName?.contains(q, true) == true
    }
}

@Serializable
data class FdScorePair(val home: Int? = null, val away: Int? = null)

@Serializable
data class FdScore(val winner: String? = null, val fullTime: FdScorePair = FdScorePair())

@Serializable
data class FdMatch(
    val id: Long = 0,
    val utcDate: String = "",
    val status: String = "",
    val matchday: Int? = null,
    val stage: String? = null,
    val homeTeam: FdTeam = FdTeam(),
    val awayTeam: FdTeam = FdTeam(),
    val score: FdScore = FdScore(),
)

@Serializable
data class FdCompetition(val name: String? = null, val code: String? = null, val emblem: String? = null)

@Serializable
data class FdMatchesResponse(
    val competition: FdCompetition? = null,
    val matches: List<FdMatch> = emptyList(),
)

@Serializable
data class FdTableRow(
    val position: Int = 0,
    val team: FdTeam = FdTeam(),
    val playedGames: Int = 0,
    val won: Int = 0,
    val draw: Int = 0,
    val lost: Int = 0,
    val points: Int = 0,
    val goalDifference: Int = 0,
)

@Serializable
data class FdStanding(
    val stage: String? = null,
    val type: String? = null,
    val group: String? = null,
    val table: List<FdTableRow> = emptyList(),
)

@Serializable
data class FdStandingsResponse(
    val competition: FdCompetition? = null,
    val standings: List<FdStanding> = emptyList(),
)

/** Everything the widget renders, assembled from cached responses. */
data class FootballData(
    val competition: FdCompetition?,
    val recent: List<FdMatch>,
    val upcoming: List<FdMatch>,
    val standings: List<FdStanding>,
    val lastUpdated: Long?,
) {
    val latestFinished: FdMatch? get() = recent.firstOrNull()
    val nextMatch: FdMatch? get() = upcoming.firstOrNull()
}

/** Competitions available on the free tier that the config screen offers. */
val FREE_COMPETITIONS = listOf(
    "CL" to "UEFA Champions League",
    "PL" to "Premier League",
    "PD" to "LaLiga",
    "SA" to "Serie A",
    "BL1" to "Bundesliga",
    "FL1" to "Ligue 1",
)
