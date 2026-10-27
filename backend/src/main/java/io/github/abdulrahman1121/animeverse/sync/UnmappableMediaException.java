package io.github.abdulrahman1121.animeverse.sync;

/**
 * Thrown when one AniList show can't be turned into a valid Anime row (missing title,
 * an enum value we don't know, a score outside 0..100, ...). The sync service catches it
 * per show, logs a warning and moves on, so a single bad record never aborts a whole sync.
 */
public class UnmappableMediaException extends RuntimeException {

	private final Integer anilistId;

	public UnmappableMediaException(Integer anilistId, String reason) {
		super("AniList media " + anilistId + " can't be mapped: " + reason);
		this.anilistId = anilistId;
	}

	public UnmappableMediaException(Integer anilistId, String reason, Throwable cause) {
		super("AniList media " + anilistId + " can't be mapped: " + reason, cause);
		this.anilistId = anilistId;
	}

	public Integer getAnilistId() {
		return anilistId;
	}
}
