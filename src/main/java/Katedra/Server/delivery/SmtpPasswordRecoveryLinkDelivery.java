package Katedra.Server.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** SMTP adapter. It deliberately never logs the recovery link or mail body. */
@Component
@ConditionalOnProperty(prefix = "app.password-recovery.email", name = "enabled", havingValue = "true")
public class SmtpPasswordRecoveryLinkDelivery implements PasswordRecoveryLinkDelivery {

    private static final DateTimeFormatter EXPIRATION_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpPasswordRecoveryLinkDelivery(
            JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Value("${app.password-recovery.email.from:}")
            String fromAddress,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}") String smtpHost
    ) {
        if (fromAddress == null || fromAddress.isBlank() || smtpHost == null || smtpHost.isBlank()) {
            throw new IllegalStateException("SMTP host and sender address must be configured when email delivery is enabled");
        }
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void deliver(String email, URI recoveryLink, LocalDateTime expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Recupera tu contraseña de Katedra");
        message.setText("Recibimos una solicitud para recuperar tu contraseña de Katedra.\n\n"
                + "Usa este enlace para elegir una nueva contraseña:\n"
                + recoveryLink + "\n\n"
                + "El enlace vence el " + expiresAt.format(EXPIRATION_FORMAT) + ". "
                + "Si no solicitaste este cambio, puedes ignorar este correo.");

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // Transport exceptions can contain message details. Do not retain or log them.
            throw new IllegalStateException("Password recovery email delivery failed");
        }
    }
}
