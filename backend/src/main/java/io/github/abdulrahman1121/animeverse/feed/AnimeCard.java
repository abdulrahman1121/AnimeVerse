package io.github.abdulrahman1121.animeverse.feed;

import java.util.Comparator;
import java.util.List;

import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.Genre;

/**
 * One card in the feed, exactly as the API sends it: a DTO (data transfer object). It holds only
 * what the screen needs, with display text already prepared, so internal fields such as the
 * AniList id and the sync time never leave the backend. Null means "nothing to show".
 *
 * @param averageScore null for an unrated show: the React card shows its NEW badge
 */
public record AnimeCard(
		Long id,
		String titleRomaji,
		String titleEnglish,
		String coverImageUrl,
		String format,
		String season,
		List<String> genres,
		String episodes,
		Integer averageScore,
		String startDate,
		String endDate) {

	/** @param airedEpisode the highest episode that has aired, or null if none has */
	static AnimeCard from(Anime anime, Short airedEpisode) {
		return new AnimeCard(
				anime.getId(),
				anime.getTitleRomaji(),
				anime.getTitleEnglish(),
				anime.getCoverImageUrl(),
				CardLabels.format(anime.getFormat()),
				CardLabels.season(anime.getSeason(), anime.getSeasonYear()),
				anime.getGenres().stream().map(Genre::getName).sorted(Comparator.naturalOrder()).toList(),
				CardLabels.episodes(airedEpisode, anime.getEpisodes()),
				anime.getAverageScore() == null ? null : anime.getAverageScore().intValue(),
				CardLabels.date(anime.getStartDate(), anime.getStartPrecision()),
				CardLabels.date(anime.getEndDate(), anime.getEndPrecision()));
	}
}
