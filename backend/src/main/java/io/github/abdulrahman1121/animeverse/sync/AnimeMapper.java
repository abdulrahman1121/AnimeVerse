package io.github.abdulrahman1121.animeverse.sync;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.AnimeStatus;
import io.github.abdulrahman1121.animeverse.anime.Season;

/**
 * Translates AniList's JSON shape (AniListMedia) into our entity (Anime). Pure logic: no
 * Spring, no database, so it is unit-tested in milliseconds.
 *
 * It does NOT touch genres: those need database lookups, so the sync service handles
 * them using genreNames().
 */
public final class AnimeMapper {

	private AnimeMapper() {
	}

	/**
	 * Creates a new Anime, or updates {@code existing} in place (same object, so JPA's
	 * dirty checking turns the changes into an UPDATE).
	 *
	 * @throws UnmappableMediaException if the show is unusable
	 */
	public static Anime map(AniListMedia media, Anime existing, Instant syncedAt) {
		if (media == null || media.id() == null) {
			throw new UnmappableMediaException(null, "missing AniList id");
		}
		Integer id = media.id();

		String romaji = media.title() == null ? null : blankToNull(media.title().romaji());
		if (romaji == null) {
			throw new UnmappableMediaException(id, "missing romaji title");
		}
		AnimeFormat format = parseEnum(AnimeFormat.class, media.format(), "format", id);
		AnimeStatus status = parseEnum(AnimeStatus.class, media.status(), "status", id);
		// Season is optional (many ONAs have none), but an unknown non-null value is an error.
		Season season = media.season() == null ? null : parseEnum(Season.class, media.season(), "season", id);

		Short episodes = null;
		if (media.episodes() != null && media.episodes() > 0) {
			episodes = toShort(media.episodes(), "episodes", id);
		}

		// AniList uses null for "not enough ratings". We also treat 0 as unrated (assumption),
		// so the UI shows NEW instead of a misleading 0.
		Short score = null;
		if (media.averageScore() != null && media.averageScore() != 0) {
			if (media.averageScore() < 0 || media.averageScore() > 100) {
				throw new UnmappableMediaException(id, "averageScore out of range: " + media.averageScore());
			}
			score = media.averageScore().shortValue();
		}

		Short seasonYear = media.seasonYear() == null ? null : toShort(media.seasonYear(), "seasonYear", id);

		PartialDate start;
		PartialDate end;
		try {
			start = PartialDate.from(media.startDate());
			end = PartialDate.from(media.endDate());
		} catch (DateTimeException e) {
			throw new UnmappableMediaException(id, "invalid date", e);
		}

		if (existing != null && !id.equals(existing.getAnilistId())) {
			throw new IllegalArgumentException(
					"existing anime has anilistId " + existing.getAnilistId() + " but media is " + id);
		}
		Anime anime = existing;
		if (anime == null) {
			anime = new Anime(id, romaji, format, status, syncedAt);
		} else {
			anime.setTitleRomaji(romaji);
			anime.setFormat(format);
			anime.setStatus(status);
			anime.setSyncedAt(syncedAt);
		}

		anime.setTitleEnglish(media.title().english() == null ? null : blankToNull(media.title().english()));
		anime.setCoverImageUrl(media.coverImage() == null ? null : blankToNull(media.coverImage().large()));
		anime.setSeason(season, seasonYear);
		anime.setEpisodes(episodes);
		anime.setAverageScore(score);
		anime.setStart(start == null ? null : start.date(), start == null ? null : start.precision());
		anime.setEnd(end == null ? null : end.date(), end == null ? null : end.precision());
		return anime;
	}

	/** Trimmed, de-duplicated, null-safe genre names, in AniList's order. */
	public static Set<String> genreNames(AniListMedia media) {
		Set<String> names = new LinkedHashSet<>();
		if (media != null && media.genres() != null) {
			for (String g : media.genres()) {
				String name = blankToNull(g);
				if (name != null) {
					names.add(name);
				}
			}
		}
		return names;
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field, Integer id) {
		if (value == null) {
			throw new UnmappableMediaException(id, "missing " + field);
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw new UnmappableMediaException(id, "unknown " + field + " '" + value + "'", e);
		}
	}

	private static Short toShort(int value, String field, Integer id) {
		if (value > Short.MAX_VALUE || value < Short.MIN_VALUE) {
			throw new UnmappableMediaException(id, field + " out of range: " + value);
		}
		return (short) value;
	}
}
