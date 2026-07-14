package Katedra.Server.controller;

import Katedra.Server.dto.AssistantRequestDTO;
import Katedra.Server.dto.AssistantResponseDTO;
import Katedra.Server.service.AssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public CompletableFuture<ResponseEntity<AssistantResponseDTO>> chat(
            @RequestBody AssistantRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return assistantService.corregir(request.temarioId(), userEmail, request)
                .thenApply(ResponseEntity::ok);
    }
}
