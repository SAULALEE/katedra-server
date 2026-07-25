package Katedra.Server.service.export;

import Katedra.Server.model.PiezaMaterial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NombreArchivoTest {

    @Test
    void deberiaGenerarSlugSinAcentosNiEspacios() {
        assertThat(NombreArchivo.slug("Matemáticas para la Ingeniería"))
                .isEqualTo("matematicas-para-la-ingenieria");
    }

    @Test
    void deberiaEliminarCaracteresPeligrososParaLaCabecera() {
        String slug = NombreArchivo.slug("Álgebra/\"Lineal\"\r\n; rm -rf");

        assertThat(slug).matches("[a-z0-9-]+");
        assertThat(slug).doesNotContain("\r", "\n", "\"", "/");
    }

    @Test
    void deberiaColapsarYRecortarLosGuiones() {
        assertThat(NombreArchivo.slug("  ---Física   ¡Cuántica!  ")).isEqualTo("fisica-cuantica");
    }

    @Test
    void deberiaTruncarSegmentosMuyLargos() {
        String largo = "a".repeat(200);

        assertThat(NombreArchivo.slug(largo)).hasSize(60);
    }

    @Test
    void deberiaUsarUnRelenoCuandoElTextoNoAportaCaracteresValidos() {
        assertThat(NombreArchivo.slug("¿¡···!?")).isEqualTo("material");
        assertThat(NombreArchivo.slug(null)).isEqualTo("material");
    }

    @Test
    void deberiaConstruirElNombreConMateriaTemarioPiezaYExtension() {
        String nombre = NombreArchivo.construir(
                "Matemáticas", "Matemáticas para la Ingeniería", PiezaMaterial.TEORIA, "pdf");

        assertThat(nombre).isEqualTo("matematicas-matematicas-para-la-ingenieria-teoria.pdf");
    }
}
