package Katedra.Server.service;

import Katedra.Server.dto.PlanPublicoResponseDTO;
import Katedra.Server.model.CicloFacturacion;
import com.stripe.model.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PlanPublicoServiceTest {

    @Mock
    private StripeService stripeService;

    @Test
    void listaGratisYPreciosRealesDeStripe() {
        Price mensual = precio(1900L, "mxn");
        Price anual = precio(18000L, "mxn");
        given(stripeService.recuperarPrecio(CicloFacturacion.MENSUAL)).willReturn(mensual);
        given(stripeService.recuperarPrecio(CicloFacturacion.ANUAL)).willReturn(anual);

        List<PlanPublicoResponseDTO> planes = new PlanPublicoService(stripeService).listar();

        assertThat(planes).extracting(PlanPublicoResponseDTO::id)
                .containsExactly("free", "pro-mensual", "pro-anual");
        assertThat(planes.get(0).precio()).isZero();
        assertThat(planes.get(1).precio()).isEqualTo(1900L);
        assertThat(planes.get(2).precio()).isEqualTo(18000L);
        assertThat(planes.get(1).moneda()).isEqualTo("mxn");
    }

    private Price precio(long monto, String moneda) {
        Price price = new Price();
        price.setUnitAmount(monto);
        price.setCurrency(moneda);
        return price;
    }
}
