package com.DockerOps;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
public class DockerOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DockerOpsApplication.class, args);
	}

}
