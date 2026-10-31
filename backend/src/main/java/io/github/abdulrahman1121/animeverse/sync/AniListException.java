package io.github.abdulrahman1121.animeverse.sync;

/** Anything that goes wrong talking to AniList that we can't recover from inside the client. */
public class AniListException extends RuntimeException {

	public AniListException(String message) {
		super(message);
	}

	public AniListException(String message, Throwable cause) {
		super(message, cause);
	}
}
