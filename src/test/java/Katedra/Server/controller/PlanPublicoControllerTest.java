package Katedra.Server.controller;

import Katedra.Server.dto.PlanPublicoResponseDTO;
import Katedra.Server.service.PlanPublicoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlanPublicoController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class PlanPublicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanPublicoService planPublicoService;

    @MockitoBean
    private Katedra.Server.service.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private Katedra.Server.repository.UsuarioRepository usuarioRepository;

    @Test
    void listaPlanesPublicosSinAutenticacion() throws Exception {
        when(planPublicoService.listar()).thenReturn(List.of(
                new PlanPublicoResponseDTO(
                        "pro",
                        "Katedra Pro",
                        "mensual",
                        1900L,
                        "mxn",
                        false,
                        List.of("100 generaciones al día"))));

        mockMvc.perform(get("/suscripciones/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pro"))
                .andExpect(jsonPath("$[0].precio").value(1900))
                .andExpect(jsonPath("$[0].caracteristicas[0]").value("100 generaciones al día"));
    }

    @Test
    void configuracionStripeAusenteDevuelve503() throws Exception {
        when(planPublicoService.listar()).thenThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Los pagos no están disponibles en este momento."));

        mockMvc.perform(get("/suscripciones/planes"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Los pagos no están disponibles en este momento."));
    }
}
