package Katedra.Server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = OAuth2ClientAutoConfiguration.class)
@EnableAsync
public class KatedraServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(KatedraServerApplication.class, args);
	}

}
