package com.example.demo;

import com.example.demo.security.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class DemoApplicationTests {

	// The service won't start without a signing key; tests make a fresh one.
	@DynamicPropertySource
	static void signingKey(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.signing-key", TestKeys::rsaPkcs8Pem);
	}

	@Test
	void contextLoads() {
	}

}
