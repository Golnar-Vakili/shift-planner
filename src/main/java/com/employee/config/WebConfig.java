package com.employee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		Path dir = Paths.get(System.getProperty("user.dir"), "user-photos").normalize().toAbsolutePath();
		registry.addResourceHandler("/user-photos/**")
				.addResourceLocations(dir.toUri().toString())
				.setCachePeriod(60 * 60 * 24 * 7);
	}
}

