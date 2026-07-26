package Katedra.Server.controller;

import Katedra.Server.dto.PlanPublicoResponseDTO;
import Katedra.Server.service.PlanPublicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/suscripciones/planes")
public class PlanPublicoController {

    private final PlanPublicoService planPublicoService;

    public PlanPublicoController(PlanPublicoService planPublicoService) {
        this.planPublicoService = planPublicoService;
    }

    @GetMapping
    public ResponseEntity<List<PlanPublicoResponseDTO>> listar() {
        return ResponseEntity.ok(planPublicoService.listar());
    }
}
