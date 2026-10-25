package io.github.abdulrahman1121.animeverse.anime;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Long> {

	/** SELECT * FROM genre WHERE name = ? (uses the UNIQUE index on name). */
	Optional<Genre> findByName(String name);
}
