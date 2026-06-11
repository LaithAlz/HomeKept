package com.homekept;

import org.springframework.boot.SpringApplication;

public class TestHomeKeptApplication {

	public static void main(String[] args) {
		SpringApplication.from(HomeKeptApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
