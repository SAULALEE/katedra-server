package Katedra.Server.student.delivery;

import java.net.URI;
import java.time.LocalDateTime;

/** Delivery port for recovery links; implementations must never log the link. */
public interface PasswordRecoveryLinkDelivery {

    void deliver(String email, URI recoveryLink, LocalDateTime expiresAt);
}
