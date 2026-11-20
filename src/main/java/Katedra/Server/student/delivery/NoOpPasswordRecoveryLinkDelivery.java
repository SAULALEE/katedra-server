package Katedra.Server.student.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;

/** Placeholder until the email delivery adapter is implemented. Intentionally does not log or retain the link. */
@Component
@ConditionalOnProperty(prefix = "app.password-recovery.email", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoOpPasswordRecoveryLinkDelivery implements PasswordRecoveryLinkDelivery {

    @Override
    public void deliver(String email, URI recoveryLink, LocalDateTime expiresAt) {
        // SMTP delivery is intentionally deferred.
    }
}
