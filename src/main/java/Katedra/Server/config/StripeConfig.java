package Katedra.Server.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente de Stripe inyectable.
 *
 * <p>Se usa {@link StripeClient} y no el estático {@code Stripe.apiKey} para que
 * {@code StripeService} reciba su dependencia por constructor como el resto de servicios
 * del proyecto, y pueda mockearse entero en los tests sin tocar estado global.
 *
 * <p>La clave puede venir vacía: sin Doppler (tests, arranque limpio) el bean se crea
 * igual y sólo falla si alguien intenta cobrar de verdad. Fallar en el arranque haría
 * que ningún test que levante contexto pudiera correr.
 */
@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(@Value("${stripe.secret-key:}") String secretKey) {
        return StripeClient.builder().setApiKey(secretKey).build();
    }
}
