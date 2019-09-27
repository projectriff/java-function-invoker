package com.acme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Currency;
import java.util.function.Function;

@SpringBootApplication
public class ConvertersApplication {

    @Bean
    public Function<Person, Salary> theFn() {
        return p -> Salary.of(1000L * p.getFirstName().length(), p.getLastName().contains("é")
                ? Currency.getInstance("EUR")
                : Currency.getInstance("USD"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ConvertersApplication.class, args);
    }
}
