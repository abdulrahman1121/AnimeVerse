package io.github.abdulrahman1121.animeverse.anime;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation at startup. JpaRepository<Anime, Long> already gives
 * save, findById, findAll, delete, count... The methods below are "derived queries": Spring
 * reads the method name and builds the SQL from it.
 */
public interface AnimeRepository extends JpaRepository<Anime, Long> {

	/** SELECT * FROM anime WHERE anilist_id = ? (uses the UNIQUE index). The sync job's "is this show new?" check. */
	Optional<Anime> findByAnilistId(Integer anilistId);
}
