package com.homekept;

import com.homekept.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class HomeKeptApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomeKeptApplication.class, args);
	}

}
