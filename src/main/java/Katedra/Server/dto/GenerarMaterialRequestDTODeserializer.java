package Katedra.Server.dto;

import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.PiezaMaterial;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class GenerarMaterialRequestDTODeserializer extends JsonDeserializer<GenerarMaterialRequestDTO> {
    @Override
    public GenerarMaterialRequestDTO deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode root = p.getCodec().readTree(p);

        Set<PiezaMaterial> piezas = new HashSet<>();
        if (root.has("piezas") && root.get("piezas").isArray()) {
            for (JsonNode piezaNode : (ArrayNode) root.get("piezas")) {
                piezas.add(PiezaMaterial.fromValor(piezaNode.asText()));
            }
        }

        ModeloIA modelo = null;
        if (root.has("modelo") && root.get("modelo") != null) {
            modelo = ModeloIA.fromValor(root.get("modelo").asText());
        }

        Set<PiezaMaterial> regenerarPiezas = new HashSet<>();
        if (root.has("regenerarPiezas") && root.get("regenerarPiezas").isArray()) {
            for (JsonNode piezaNode : (ArrayNode) root.get("regenerarPiezas")) {
                regenerarPiezas.add(PiezaMaterial.fromValor(piezaNode.asText()));
            }
        }

        Integer numeroDiapositivas = root.has("numeroDiapositivas") && root.get("numeroDiapositivas") != null
                ? root.get("numeroDiapositivas").asInt()
                : null;

        Integer numeroParrafos = root.has("numeroParrafos") && root.get("numeroParrafos") != null
                ? root.get("numeroParrafos").asInt()
                : null;

        Integer numeroPreguntas = root.has("numeroPreguntas") && root.get("numeroPreguntas") != null
                ? root.get("numeroPreguntas").asInt()
                : null;

        Integer numeroModulos = root.has("numeroModulos") && root.get("numeroModulos") != null
                ? root.get("numeroModulos").asInt()
                : null;

        return new GenerarMaterialRequestDTO(piezas, modelo, regenerarPiezas, numeroDiapositivas, numeroParrafos, numeroPreguntas, numeroModulos);
    }
}
