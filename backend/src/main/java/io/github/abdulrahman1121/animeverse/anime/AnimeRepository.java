package io.github.abdulrahman1121.animeverse.anime;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data writes the implementation at startup. JpaRepository<Anime, Long> already gives
 * save, findById, findAll, delete, count... The methods below are "derived queries": Spring
 * reads the method name and builds the SQL from it. The feed queries are written out with
 * @Query (JPQL: it names the entity and its fields, not the table and columns).
 *
 * Every feed query uses "left join fetch a.genres": JPA loads each show together with its genres
 * in ONE query, instead of one extra query per show later (the N+1 problem). "Left" keeps shows
 * that have no genres. Hibernate merges the repeated rows back into one Anime per show.
 */
public interface AnimeRepository extends JpaRepository<Anime, Long> {

	/** SELECT * FROM anime WHERE anilist_id = ? (uses the UNIQUE index). The sync job's "is this show new?" check. */
	Optional<Anime> findByAnilistId(Integer anilistId);

	/** Airing now: newest premiere first, shows with no start date last (uses idx_anime_status_start_date). */
	@Query("""
			select a from Anime a left join fetch a.genres
			where a.status = io.github.abdulrahman1121.animeverse.anime.AnimeStatus.RELEASING
			order by a.startDate desc nulls last, a.id desc
			""")
	List<Anime> findAiring();

	/** Finished since the given day (inclusive): most recently ended first (uses idx_anime_status_end_date). */
	@Query("""
			select a from Anime a left join fetch a.genres
			where a.status = io.github.abdulrahman1121.animeverse.anime.AnimeStatus.FINISHED
			  and a.endDate >= :from
			order by a.endDate desc, a.id desc
			""")
	List<Anime> findFinishedSince(@Param("from") LocalDate from);

	/** Not yet released, starting in [from, to): soonest first. A show with no start date never matches. */
	@Query("""
			select a from Anime a left join fetch a.genres
			where a.status = io.github.abdulrahman1121.animeverse.anime.AnimeStatus.NOT_YET_RELEASED
			  and a.startDate >= :from and a.startDate < :to
			order by a.startDate asc, a.id asc
			""")
	List<Anime> findUpcoming(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
