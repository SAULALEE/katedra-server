package Katedra.Server.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OAuthClientsAvailableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return hasProviderCredentials(context, "oauth.google")
                || hasProviderCredentials(context, "oauth.microsoft");
    }

    private boolean hasProviderCredentials(ConditionContext context, String providerPrefix) {
        String clientId = context.getEnvironment().getProperty(providerPrefix + ".client-id");
        String clientSecret = context.getEnvironment().getProperty(providerPrefix + ".client-secret");
        return hasText(clientId) && hasText(clientSecret);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
