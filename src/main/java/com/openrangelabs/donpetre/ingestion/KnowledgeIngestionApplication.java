package com.openrangelabs.donpetre.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

@SpringBootApplication(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class
})
public class KnowledgeIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeIngestionApplication.class, args);
    }

}
