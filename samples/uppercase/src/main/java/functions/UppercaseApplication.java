package functions;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UppercaseApplication {

	@Bean
	public Function<String, String> uppercase() {
		return s -> s.toUpperCase();
	}

	public static void main(String[] args) {
		SpringApplication.run(UppercaseApplication.class, args);
	}
}
