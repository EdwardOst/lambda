package com.talend.se.cloud.function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SpringBootApplication
public class TalendFunctionWrapper {

	public static void main(String[] args) {
		SpringApplication.run(TalendFunctionWrapper.class, args);
	}

	@Bean
	public Function<String, String> reverseString() {
		return value -> new StringBuilder(value).reverse().toString();
	}
	
//	@Bean
//	public Function<String, String> reverseString() {
//		return value -> new StringBuilder(value).reverse().toString();
//	}

}