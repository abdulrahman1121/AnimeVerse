package io.github.abdulrahman1121.animeverse.sync;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeRepository;
import io.github.abdulrahman1121.animeverse.anime.Genre;
import io.github.abdulrahman1121.animeverse.anime.GenreRepository;

/**
 * Saves ONE page of shows (up to 50) inside ONE database transaction.
 *
 * This is its own bean on purpose. Spring implements @Transactional by wrapping the bean in a
 * proxy that opens the transaction before the method and commits after it. A call from another
 * method of the SAME class skips the proxy, so the annotation would silently do nothing.
 * AnimeSyncService therefore calls this class from the outside, once per page.
 */
@Component
public class AnimePageSaver {

	private static final Logger log = LoggerFactory.getLogger(AnimePageSaver.class);

	public record PageResult(int saved, int skipped) {
	}

	private final AnimeRepository animeRepository;
	private final GenreRepository genreRepository;

	public AnimePageSaver(AnimeRepository animeRepository, GenreRepository genreRepository) {
		this.animeRepository = animeRepository;
		this.genreRepository = genreRepository;
	}

	/**
	 * Upserts every show on the page: update the row if its AniList id is already known,
	 * insert otherwise. A show that can't be mapped is logged and skipped. That is safe inside
	 * the transaction because the mapper validates before it changes anything, so nothing
	 * half-written is left behind. A real database error propagates, which rolls back the
	 * whole page.
	 */
	@Transactional
	public PageResult save(List<AniListMedia> page, Instant syncedAt) {
		Map<String, Genre> genreCache = new HashMap<>(); // avoids re-querying "Action" for every show
		int saved = 0;
		int skipped = 0;
		for (AniListMedia media : page) {
			try {
				Anime existing = media == null || media.id() == null ? null
						: animeRepository.findByAnilistId(media.id()).orElse(null);
				Anime anime = AnimeMapper.map(media, existing, syncedAt);
				mirrorGenres(anime, AnimeMapper.genreNames(media), genreCache);
				animeRepository.save(anime);
				saved++;
			} catch (UnmappableMediaException e) {
				log.warn("Skipping show: {}", e.getMessage());
				skipped++;
			}
		}
		return new PageResult(saved, skipped);
	}

	/** Makes the anime's genres exactly match AniList's: remove the ones it dropped, add the new ones. */
	private void mirrorGenres(Anime anime, Set<String> wanted, Map<String, Genre> cache) {
		for (Genre current : new HashSet<>(anime.getGenres())) {
			if (!wanted.contains(current.getName())) {
				anime.removeGenre(current);
			}
		}
		for (String name : wanted) {
			Genre genre = cache.computeIfAbsent(name,
					n -> genreRepository.findByName(n).orElseGet(() -> genreRepository.save(new Genre(n))));
			anime.addGenre(genre); // a Set ignores it if the show already has this genre
		}
	}
}
