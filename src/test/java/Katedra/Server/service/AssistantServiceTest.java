package Katedra.Server.service;

import Katedra.Server.dto.AssistantRequestDTO;
import Katedra.Server.dto.AssistantResponseDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import Katedra.Server.model.Temario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final String TEMARIO_ID = "temario-1";
    private static final String USER_EMAIL = "docente@katedra.test";

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ContenidoTemarioService contenidoTemarioService;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AssistantService service;

    // Runs supplyAsync's task synchronously on the calling thread so tests stay deterministic
    // (no real threading) and finish instantly, without ever risking the 10s orTimeout race.
    private static final Executor SYNC_EXECUTOR = Runnable::run;

    @BeforeEach
    void setUp() {
        given(chatClientBuilder.build()).willReturn(chatClient);
        service = new AssistantService(
                chatClientBuilder, contenidoTemarioService, usuarioRepository, SYNC_EXECUTOR);
    }

    private ContenidoTemario contenidoConTeoria(String teoria) {
        Usuario usuario = new Usuario();
        usuario.setEmail(USER_EMAIL);
        ReflectionTestUtils.setField(usuario, "id", "usuario-1");
        Asignatura asignatura = new Asignatura(usuario, "Programacion", null);
        ReflectionTestUtils.setField(asignatura, "id", "asignatura-1");
        Temario temario = new Temario(usuario, "Pilas", "Pilas y colas", "universitario", asignatura);
        ContenidoTemario contenido = new ContenidoTemario(temario);
        contenido.setTeoria(teoria);
        contenido.setModelo(ModeloIA.FLASH.getValor());
        return contenido;
    }

    private void stubContent(String response) {
        given(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .content()).willReturn(response);
    }

    @Test
    void shouldCorrectTheoryWithQuickAction() throws ExecutionException, InterruptedException {
        ContenidoTemario contenido = contenidoConTeoria("## Teoría original de tres párrafos.");
        given(contenidoTemarioService.getContenidoOwned(TEMARIO_ID, USER_EMAIL)).willReturn(contenido);
        stubContent("## Teoría acortada.");

        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.ACORTAR, "a 4 párrafos", ModeloIA.FLASH);
        AssistantResponseDTO response = service.corregir(TEMARIO_ID, USER_EMAIL, request).get();

        assertThat(response.corregido()).isTrue();
        assertThat(response.content()).isEqualTo("## Teoría acortada.");
        assertThat(response.modeloUsed()).isEqualTo(ModeloIA.BASICO);
        verify(contenidoTemarioService).guardarTeoria(contenido, "## Teoría acortada.");
        verify(usuarioRepository).incrementAiGenerationCount("usuario-1", 1L);
    }

    @Test
    void shouldRefuseOffTopicFreeChatWithoutCallingLlm() throws ExecutionException, InterruptedException {
        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.FREE_CHAT, "cual es la capital de Francia?", null);

        AssistantResponseDTO response = service.corregir(TEMARIO_ID, USER_EMAIL, request).get();

        assertThat(response.corregido()).isFalse();
        assertThat(response.modeloUsed()).isNull();
        verify(contenidoTemarioService, never()).getContenidoOwned(anyString(), anyString());
        verify(contenidoTemarioService, never()).guardarTeoria(any(), anyString());
        verify(usuarioRepository, never()).incrementAiGenerationCount(anyString(), anyLong());
    }

    @Test
    void shouldRefuseGreetingFreeChat() throws ExecutionException, InterruptedException {
        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.FREE_CHAT, "hola, como estas?", null);

        AssistantResponseDTO response = service.corregir(TEMARIO_ID, USER_EMAIL, request).get();

        assertThat(response.corregido()).isFalse();
        verify(contenidoTemarioService, never()).getContenidoOwned(anyString(), anyString());
        verify(usuarioRepository, never()).incrementAiGenerationCount(anyString(), anyLong());
    }

    @Test
    void shouldAcceptFreeChatWithEditIntentKeyword() throws ExecutionException, InterruptedException {
        ContenidoTemario contenido = contenidoConTeoria("## Teoría original.");
        given(contenidoTemarioService.getContenidoOwned(TEMARIO_ID, USER_EMAIL)).willReturn(contenido);
        stubContent("## Teoría simplificada.");

        AssistantRequestDTO request = new AssistantRequestDTO(
                TEMARIO_ID, AssistantQuickAction.FREE_CHAT, "simplifica el lenguaje por favor", null);
        AssistantResponseDTO response = service.corregir(TEMARIO_ID, USER_EMAIL, request).get();

        assertThat(response.corregido()).isTrue();
        assertThat(response.content()).isEqualTo("## Teoría simplificada.");
    }

    @Test
    void shouldRejectBlankFreeChatMessage() {
        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.FREE_CHAT, "  ", null);

        // @Async is not proxied under plain Mockito (no Spring context), so the validation
        // exception throws synchronously here; in production it surfaces via a failed future,
        // which Spring MVC unwraps to the same ResponseStatusException/HTTP status.
        assertThatThrownBy(() -> service.corregir(TEMARIO_ID, USER_EMAIL, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chat libre");
    }

    @Test
    void shouldRejectWhenTheoryNotYetGenerated() {
        ContenidoTemario contenido = contenidoConTeoria(null);
        given(contenidoTemarioService.getContenidoOwned(TEMARIO_ID, USER_EMAIL)).willReturn(contenido);

        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.ACORTAR, null, null);

        assertThatThrownBy(() -> service.corregir(TEMARIO_ID, USER_EMAIL, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Genera la teoría primero");
    }

    @Test
    void shouldAlwaysUseFlashRegardlessOfRequestedModel() throws ExecutionException, InterruptedException {
        // Corrections must never route to a reasoning tier (30-90s+): the requested MAX tier
        // is ignored and the call always runs on FLASH, the fast non-reasoning model.
        ContenidoTemario contenido = contenidoConTeoria("## Teoría original.");
        given(contenidoTemarioService.getContenidoOwned(TEMARIO_ID, USER_EMAIL)).willReturn(contenido);
        stubContent("## Teoría corregida.");

        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.ACORTAR, null, ModeloIA.MAX);
        AssistantResponseDTO response = service.corregir(TEMARIO_ID, USER_EMAIL, request).get();

        assertThat(response.modeloUsed()).isEqualTo(ModeloIA.BASICO);
    }

    @Test
    void shouldReturnGracefulMessageWhenLlmCallFails() throws ExecutionException, InterruptedException {
        ContenidoTemario contenido = contenidoConTeoria("## Teoría original.");
        given(contenidoTemarioService.getContenidoOwned(TEMARIO_ID, USER_EMAIL)).willReturn(contenido);
        given(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()).willThrow(new RuntimeException("API unavailable"));

        AssistantRequestDTO request = new AssistantRequestDTO(TEMARIO_ID, AssistantQuickAction.EXTENDER, null, null);
        AssistantResponseDTO response = service.corregir(TEMARIO_ID, USER_EMAIL, request).get();

        assertThat(response.corregido()).isFalse();
        verify(contenidoTemarioService, never()).guardarTeoria(any(), anyString());
        verify(usuarioRepository, never()).incrementAiGenerationCount(anyString(), anyLong());
    }
}
