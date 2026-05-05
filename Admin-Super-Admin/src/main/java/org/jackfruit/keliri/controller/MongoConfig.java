package org.jackfruit.keliri.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

@Component
@Configuration
public class MongoConfig {
	 @Bean
	    public MongoDatabase mongoDatabase() {
	       MongoClient mongoClient = MongoClients.create("mongodb+srv://sonu:sonu@cluster0.g1gd0ah.mongodb.net/?appName=Cluster0");
	       return mongoClient.getDatabase("keliri");
	    }
}
