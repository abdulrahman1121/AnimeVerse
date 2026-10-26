package io.github.abdulrahman1121.animeverse.sync;

import java.util.List;

/**
 * One show exactly as AniList's GraphQL API returns it. Field names match the JSON keys,
 * so Jackson (the JSON library inside Spring) fills these records in automatically.
 *
 * A record is a compact, immutable data class: Java generates the constructor, the
 * accessors (media.title(), not getTitle()), equals, hashCode and toString.
 */
public record AniListMedia(
		Integer id,
		Title title,
		String format,
		String status,
		String season,          // null for many ONAs
		Integer seasonYear,
		Integer episodes,       // null while the total isn't announced
		Integer averageScore,   // null until enough people rate it
		List<String> genres,
		FuzzyDate startDate,
		FuzzyDate endDate,
		CoverImage coverImage) {

	public record Title(String romaji, String english) {
	}

	/** AniList's partial date: any part may be null, e.g. {2027, 4, null} = "April 2027". */
	public record FuzzyDate(Integer year, Integer month, Integer day) {
	}

	public record CoverImage(String large) {
	}
}
