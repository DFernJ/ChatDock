package com.DockerOps;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication()
@EnableJpaAuditing
public class DockerOpsApplication {

	public static void main(String[] args) {
		Dotenv.configure().ignoreIfMissing().load()
				.entries()
				.forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		SpringApplication.run(DockerOpsApplication.class, args);
	}

}
