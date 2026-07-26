package Katedra.Server.service;

import Katedra.Server.dto.PlanPublicoResponseDTO;
import Katedra.Server.model.CicloFacturacion;
import com.stripe.model.Price;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlanPublicoService {

    private static final List<String> GRATIS = List.of(
            "10 generaciones de IA al día",
            "5 exportaciones al día",
            "Teoría y evaluaciones",
            "Exportación a Word y PDF");

    private static final List<String> PRO = List.of(
            "100 generaciones de IA al día",
            "100 exportaciones al día",
            "Modelo Catedrático",
            "Generación de diapositivas",
            "Temarios desde PDF o enlace web",
            "Exportación a Markdown y Google Forms");

    private final StripeService stripeService;

    public PlanPublicoService(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    public List<PlanPublicoResponseDTO> listar() {
        Price mensual = stripeService.recuperarPrecio(CicloFacturacion.MENSUAL);
        Price anual = stripeService.recuperarPrecio(CicloFacturacion.ANUAL);
        return List.of(
                new PlanPublicoResponseDTO("free", "Katedra Gratis", null, 0, "mxn", false, GRATIS),
                aPlan("pro-mensual", "Katedra Pro", "mensual", mensual),
                aPlan("pro-anual", "Katedra Pro", "anual", anual));
    }

    private PlanPublicoResponseDTO aPlan(String id, String nombre, String ciclo, Price price) {
        return new PlanPublicoResponseDTO(
                id,
                nombre,
                ciclo,
                price.getUnitAmount(),
                price.getCurrency(),
                true,
                PRO);
    }
}
