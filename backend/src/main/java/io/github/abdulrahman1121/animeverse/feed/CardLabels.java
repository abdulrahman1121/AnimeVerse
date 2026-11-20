package io.github.abdulrahman1121.animeverse.feed;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.DatePrecision;
import io.github.abdulrahman1121.animeverse.anime.Season;

/**
 * Turns database values into the display text a card shows, so the React app only prints strings.
 * Pure functions: no Spring, no database. Each one returns null when there is nothing to show,
 * which the JSON carries as null.
 */
final class CardLabels {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

	private CardLabels() {
	}

	/** TV_SHORT becomes "TV Short". */
	static String format(AnimeFormat format) {
		if (format == null) {
			return null;
		}
		return switch (format) {
			case TV -> "TV";
			case TV_SHORT -> "TV Short";
			case MOVIE -> "Movie";
			case SPECIAL -> "Special";
			case OVA -> "OVA";
			case ONA -> "ONA";
			case MUSIC -> "Music";
		};
	}

	/** "Fall 2026". A season with no year is just "Fall"; no season at all is null. */
	static String season(Season season, Short year) {
		if (season == null) {
			return null;
		}
		String name = switch (season) {
			case WINTER -> "Winter";
			case SPRING -> "Spring";
			case SUMMER -> "Summer";
			case FALL -> "Fall";
		};
		return year == null ? name : name + " " + year;
	}

	/**
	 * Shows only what is actually known, matching the stored precision:
	 * DAY "Oct 4, 2026", MONTH "April 2027", YEAR "2027". No date is null.
	 */
	static String date(LocalDate date, DatePrecision precision) {
		if (date == null || precision == null) {
			return null;
		}
		return switch (precision) {
			case DAY -> DAY.format(date);
			case MONTH -> MONTH.format(date);
			case YEAR -> String.valueOf(date.getYear());
		};
	}

	/**
	 * "Ep 4 / 12" or "Ep 4 / ?" while the total is unannounced. If nothing has aired yet, a known
	 * total reads "12 episodes" and an unknown one is null. The aired number is capped at the
	 * total, so an extra or rescheduled entry can never produce "Ep 13 / 12".
	 */
	static String episodes(Short aired, Short total) {
		if (aired == null) {
			return total == null ? null : total + " episodes";
		}
		int shown = total == null ? aired : Math.min(aired, total);
		return "Ep " + shown + " / " + (total == null ? "?" : total);
	}
}
