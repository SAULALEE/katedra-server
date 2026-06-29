package Katedra.Server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "oauth.google.client-id=",
        "oauth.google.client-secret=",
        "oauth.microsoft.client-id=",
        "oauth.microsoft.client-secret=",
        "app.frontend.oauth.success-url=",
        "app.frontend.oauth.error-url="
})
class OAuthOptionalStartupTest {

    @Test
    void contextLoadsWithoutOAuthSecrets() {
    }
}
