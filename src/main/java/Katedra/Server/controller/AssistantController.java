package Katedra.Server.controller;

import Katedra.Server.dto.AssistantRequestDTO;
import Katedra.Server.dto.AssistantResponseDTO;
import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ModeloIA;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    @PostMapping("/chat")
    public ResponseEntity<AssistantResponseDTO> chatStatic(
            @RequestBody AssistantRequestDTO request,
            Authentication authentication) {
        
        ModeloIA model = request.modelo() != null ? request.modelo() : ModeloIA.FLASH;
        AssistantQuickAction action = request.action() != null ? request.action() : AssistantQuickAction.FREE_CHAT;
        
        String staticContent = "Static response for action: " + action.name() + " using model: " + model.getDisplayName();
        
        AssistantResponseDTO response = new AssistantResponseDTO(staticContent, model, action);
        return ResponseEntity.ok(response);
    }
}
