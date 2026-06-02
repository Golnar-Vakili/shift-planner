package com.employee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;

@SpringBootApplication
public class EmployeeLoginApplication {

	public static void main(String[] args) {
		System.out.println("[employee-login] Starting...");
		System.out.flush();
		SpringApplication app = new SpringApplication(EmployeeLoginApplication.class);
		app.addListeners((ApplicationListener<ApplicationFailedEvent>) event -> {
			Throwable ex = event.getException();
			System.err.println("========== APPLICATION FAILED TO START ==========");
			if (ex != null) {
				ex.printStackTrace(System.err);
			}
			System.err.flush();
		});
		app.run(args);
		System.out.println("[employee-login] Started. Open http://localhost:8080");
		System.out.flush();
	}
}
