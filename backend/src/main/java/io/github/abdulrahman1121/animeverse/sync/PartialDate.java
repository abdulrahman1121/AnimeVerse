package io.github.abdulrahman1121.animeverse.sync;

import java.time.LocalDate;

import io.github.abdulrahman1121.animeverse.anime.DatePrecision;

/**
 * Our version of a partial date: a real LocalDate (rounded to the 1st) plus how much of it
 * is actually known. Matches the anime table's date + precision column pairs.
 */
public record PartialDate(LocalDate date, DatePrecision precision) {

	/**
	 * Converts AniList's FuzzyDate. Returns null when even the year is unknown, so the
	 * caller stores NULL in both columns.
	 *
	 *   {2026, 10, 4}       -> 2026-10-04, DAY
	 *   {2027, 4, null}     -> 2027-04-01, MONTH
	 *   {2027, null, null}  -> 2027-01-01, YEAR
	 *   {null, ...} or null -> null
	 */
	public static PartialDate from(AniListMedia.FuzzyDate fuzzy) {
		if (fuzzy == null || fuzzy.year() == null) {
			return null;
		}
		if (fuzzy.month() == null) {
			return new PartialDate(LocalDate.of(fuzzy.year(), 1, 1), DatePrecision.YEAR);
		}
		if (fuzzy.day() == null) {
			return new PartialDate(LocalDate.of(fuzzy.year(), fuzzy.month(), 1), DatePrecision.MONTH);
		}
		return new PartialDate(LocalDate.of(fuzzy.year(), fuzzy.month(), fuzzy.day()), DatePrecision.DAY);
	}
}
