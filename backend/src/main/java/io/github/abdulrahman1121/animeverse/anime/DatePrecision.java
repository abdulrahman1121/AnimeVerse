package io.github.abdulrahman1121.animeverse.anime;

/**
 * How much of a date is actually known. A date stored as 2027-04-01 with MONTH precision
 * means "April 2027", not "April 1st". Matches the CHECKs on start_precision / end_precision.
 */
public enum DatePrecision {
	DAY,
	MONTH,
	YEAR
}
