package io.github.abdulrahman1121.animeverse.anime;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiringEpisodeRepository extends JpaRepository<AiringEpisode, Long> {

	/** The sync job's "do we already have episode 7 of this show?" check (uses UNIQUE (anime_id, episode)). */
	Optional<AiringEpisode> findByAnimeAndEpisode(Anime anime, Short episode);

	/**
	 * "Ep X / Y": the highest episode number that has aired by a given moment. Empty if none has.
	 * We take the MAX, not a count, because we only store the current year's episodes: a long
	 * runner like One Piece would count ~40 stored rows but its latest episode is #1100+.
	 *
	 * This is a @Query because "max" can't be expressed in a derived method name. The text is
	 * JPQL: it names the entity (AiringEpisode) and its fields, not the table and columns.
	 */
	@Query("select max(e.episode) from AiringEpisode e where e.anime.id = :animeId and e.airingAt <= :moment")
	Optional<Short> findMaxAiredEpisode(@Param("animeId") Long animeId, @Param("moment") Instant moment);

	/**
	 * The same "highest aired episode", for MANY shows in one query: one row per show that has at
	 * least one aired episode (shows with none are simply missing from the result). The feed uses
	 * this once per section instead of asking once per card.
	 *
	 * "select new ..." builds a MaxAiredEpisode record straight from each row.
	 */
	@Query("""
			select new io.github.abdulrahman1121.animeverse.anime.MaxAiredEpisode(e.anime.id, max(e.episode))
			from AiringEpisode e
			where e.anime.id in :animeIds and e.airingAt <= :moment
			group by e.anime.id
			""")
	List<MaxAiredEpisode> findMaxAiredEpisodes(@Param("animeIds") Collection<Long> animeIds, @Param("moment") Instant moment);
}
