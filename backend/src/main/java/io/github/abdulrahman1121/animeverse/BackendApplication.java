package io.github.abdulrahman1121.animeverse;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	// Run the whole app in UTC, the server standard: store and process times in UTC,
	// convert to the viewer's local time only in the UI.
	// The static block runs when main() first uses this class, before Spring opens any
	// DB connection. Tests don't call main(), so pom.xml's Surefire argLine sets UTC for them.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
