package com.chatops;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BotApp {
    public static void main(String[] args) {
        Dotenv.configure().ignoreIfMissing().load()
                .entries()
                .forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        SpringApplication.run(BotApp.class, args);
    }
}
