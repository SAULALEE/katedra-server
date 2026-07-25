package Katedra.Server.controller;

import Katedra.Server.dto.ExportacionArchivoDTO;
import Katedra.Server.service.ExportacionMaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExportacionController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class ExportacionControllerTest {

    private static final String TEMARIO_ID = "temario-uuid-123";
    private static final String EMAIL = "profesor@katedra.com";
    private static final byte[] BYTES = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
    private static final String NOMBRE_PDF = "matematicas-matematicas-para-la-ingenieria-teoria.pdf";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportacionMaterialService exportacionMaterialService;

    @MockitoBean
    private Katedra.Server.service.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private Katedra.Server.repository.UsuarioRepository usuarioRepository;

    private Principal principal;

    @BeforeEach
    void setUp() {
        principal = new UsernamePasswordAuthenticationToken(EMAIL, null);
    }

    @Test
    void deberiaDevolverElPdfComoDescargaConSuNombreDeArchivo() throws Exception {
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willReturn(new ExportacionArchivoDTO(NOMBRE_PDF, "application/pdf", BYTES));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "pdf")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(NOMBRE_PDF)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(BYTES));
    }

    @Test
    void deberiaDevolverElDocxConSuTipoDeContenido() throws Exception {
        String tipo = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willReturn(new ExportacionArchivoDTO("temario-teoria.docx", tipo, BYTES));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "docx")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", tipo));
    }

    @Test
    void deberiaDevolverElMarkdownComoTextoUtf8() throws Exception {
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willReturn(new ExportacionArchivoDTO("temario-teoria.md", "text/markdown;charset=UTF-8", BYTES));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "md")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"));
    }

    @Test
    void deberiaUsarElEmailDelPrincipalComoUsuario() throws Exception {
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willReturn(new ExportacionArchivoDTO(NOMBRE_PDF, "application/pdf", BYTES));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "pdf")
                        .principal(principal))
                .andExpect(status().isOk());

        verify(exportacionMaterialService).exportar(eq(TEMARIO_ID), eq(EMAIL), eq("teoria"), eq("pdf"), any());
    }

    /**
     * Locks in the decision NOT to declare produces= on the mapping: the global handler always
     * answers JSON, so a media-type restriction would turn this 403 into a 406 with no message.
     */
    @Test
    void deberiaResponder403EnJsonCuandoElTemarioEsDeOtroUsuario() throws Exception {
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario"));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "pdf")
                        .principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acceso denegado a este temario"));
    }

    @Test
    void deberiaResponder404CuandoNoHayContenidoGenerado() throws Exception {
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Este temario todavía no tiene material generado"));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "pdf")
                        .principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Este temario todavía no tiene material generado"));
    }

    @Test
    void deberiaResponder400CuandoElFormatoEsDesconocido() throws Exception {
        given(exportacionMaterialService.exportar(any(), any(), any(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Formato de exportación no válido: xls"));

        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("pieza", "teoria")
                        .param("formato", "xls")
                        .principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato de exportación no válido: xls"));
    }

    @Test
    void deberiaResponder400CuandoFaltaElParametroPieza() throws Exception {
        mockMvc.perform(get("/temarios/{id}/exportaciones", TEMARIO_ID)
                        .param("formato", "pdf")
                        .principal(principal))
                .andExpect(status().isBadRequest());
    }
}
