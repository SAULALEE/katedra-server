package Katedra.Server.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class SmtpPasswordRecoveryLinkDeliveryTest {

    @Test
    void shouldSendRecoveryLinkToStudentThroughConfiguredMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpPasswordRecoveryLinkDelivery delivery = new SmtpPasswordRecoveryLinkDelivery(
                mailSender, "no-reply@example.com", "smtp.example.com");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 18, 22, 15);

        delivery.deliver("student@example.com",
                URI.create("https://frontend.example/reset-password?token=one-time-secret"), expiresAt);

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getTo()).containsExactly("student@example.com");
        assertThat(message.getSubject()).isEqualTo("Recupera tu contraseña de Katedra");
        assertThat(message.getText())
                .contains("https://frontend.example/reset-password?token=one-time-secret")
                .contains("18/11/2026 22:15");
    }

    @Test
    void shouldNotExposeRecoveryLinkInExceptionWhenMailTransportFails() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("transport failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpPasswordRecoveryLinkDelivery delivery = new SmtpPasswordRecoveryLinkDelivery(
                mailSender, "no-reply@example.com", "smtp.example.com");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> delivery.deliver("student@example.com",
                        URI.create("https://frontend.example/reset-password?token=one-time-secret"),
                        LocalDateTime.of(2026, 11, 18, 22, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Password recovery email delivery failed")
                .hasNoCause();
    }
}
