package io.github.abdulrahman1121.animeverse.anime;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiringEpisodeRepository extends JpaRepository<AiringEpisode, Long> {

	/** The sync job's "do we already have episode 7 of this show?" check (uses UNIQUE (anime_id, episode)). */
	Optional<AiringEpisode> findByAnimeAndEpisode(Anime anime, Short episode);

	/**
	 * "Ep X / Y": how many episodes have aired by a given moment.
	 * SELECT count(*) FROM airing_episode WHERE anime_id = ? AND airing_at <= ?
	 * "AnimeId" walks into the anime field and uses its id.
	 */
	long countByAnimeIdAndAiringAtLessThanEqual(Long animeId, Instant moment);
}
