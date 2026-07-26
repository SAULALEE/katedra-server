package Katedra.Server.config;

import Katedra.Server.dto.PlanPublicoResponseDTO;
import Katedra.Server.service.PlanPublicoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PricingSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanPublicoService planPublicoService;

    @Test
    void planesEsPublicoPeroCheckoutEsPrivado() throws Exception {
        given(planPublicoService.listar()).willReturn(List.of(
                new PlanPublicoResponseDTO(
                        "free", "Katedra Gratis", null, 0, "mxn", false, List.of())));

        mockMvc.perform(get("/suscripciones/planes"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/suscripciones")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookEsPublicoPeroExigeFirma() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
