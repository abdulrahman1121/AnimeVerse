package io.github.abdulrahman1121.animeverse.sync;

/** One entry from AniList's top-level airing schedule: episode N of show mediaId airs at airingAt. */
public record AniListAiringSchedule(
		Integer episode,
		Long airingAt,   // Unix time: seconds since 1970-01-01 UTC
		Integer mediaId) {
}
