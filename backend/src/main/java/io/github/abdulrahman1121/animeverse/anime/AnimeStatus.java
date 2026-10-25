package io.github.abdulrahman1121.animeverse.anime;

/** Release status, using AniList's exact names so sync data maps 1:1. Matches the CHECK on anime.status. */
public enum AnimeStatus {
	RELEASING,
	FINISHED,
	NOT_YET_RELEASED,
	HIATUS,
	CANCELLED
}
