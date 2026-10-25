package io.github.abdulrahman1121.animeverse.anime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the genre table, e.g. "Action". Each name exists once. */
@Entity
@Table(name = "genre")
public class Genre {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // Postgres generates it (GENERATED ALWAYS AS IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	/** Required by JPA: Hibernate creates the empty object, then fills in fields from the row. */
	protected Genre() {
	}

	public Genre(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
