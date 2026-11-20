package io.github.abdulrahman1121.animeverse.anime;

/** One row of the "highest aired episode per show" query: which show, and its latest aired episode number. */
public record MaxAiredEpisode(Long animeId, Short episode) {
}
