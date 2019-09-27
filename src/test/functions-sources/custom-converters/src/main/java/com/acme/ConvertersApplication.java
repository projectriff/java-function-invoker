package com.acme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.function.Function;

@SpringBootApplication
public class ConvertersApplication {

    @Bean
    public Function<CustomInput, CustomOutput> theFn() {
        return i -> new CustomOutput(i.data + i.data);
    }

    @Bean
    public CustomInputConverter myCustomInputConverter() {
        return new CustomInputConverter();
    }

    public static void main(String[] args) {
        SpringApplication.run(ConvertersApplication.class, args);
    }
}
