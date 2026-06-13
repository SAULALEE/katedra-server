package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContenidoTemarioService {

    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final TemarioRepository temarioRepository;
    private final Katedra.Server.repository.UsuarioRepository usuarioRepository;

    public ContenidoTemarioService(
            ContenidoTemarioRepository contenidoTemarioRepository,
            TemarioRepository temarioRepository,
            Katedra.Server.repository.UsuarioRepository usuarioRepository) {
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.temarioRepository = temarioRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ContenidoTemarioResponseDTO getContenidoByTemarioId(String temarioId, String userEmail) {
        var temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        ContenidoTemario entity = contenidoTemarioRepository.findByTemarioId(temarioId)
            .orElseGet(() -> {
                ContenidoTemario mockContenido = new ContenidoTemario(temario);
                populateMockData(mockContenido);
                return contenidoTemarioRepository.save(mockContenido);
            });
            
        return mapToDTO(entity);
    }

    @Transactional
    public ContenidoTemarioResponseDTO generarMaterial(String temarioId, String userEmail) {
        var temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        ContenidoTemario contenido = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElse(new ContenidoTemario(temario));

        populateMockData(contenido);
        ContenidoTemario saved = contenidoTemarioRepository.save(contenido);
        return mapToDTO(saved);
    }

    @Transactional
    public ContenidoTemarioResponseDTO generarMaterialDesdeCero(Katedra.Server.dto.GenerarMaterialRequestDTO request, String userEmail) {
        var usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Katedra.Server.model.Temario temario = new Katedra.Server.model.Temario(
                usuario,
                request.tema(),
                request.unidades(),
                "Universidad",
                request.materia()
        );
        var savedTemario = temarioRepository.save(temario);

        ContenidoTemario contenido = new ContenidoTemario(savedTemario);
        populateMockData(contenido);
        ContenidoTemario savedContenido = contenidoTemarioRepository.save(contenido);

        return mapToDTO(savedContenido);
    }

    private void populateMockData(ContenidoTemario contenido) {
        contenido.setTeoria("## Unidad 1: Estructuras de Datos Lineales\n\n### 1.1 Introducción a las Estructuras de Datos\nEn programación, una estructura de datos es una forma particular de organizar y almacenar datos en una computadora para que puedan usarse de manera eficiente. Las estructuras de datos lineales son aquellas cuyos elementos forman una secuencia ordenada donde cada elemento tiene un único predecesor y sucesor directo (excepto el primero y el último).\n\n### 1.2 Listas Enlazadas (Linked Lists)\nUna lista enlazada es una colección lineal de elementos de datos, llamados nodos, donde el orden lineal no está dado por su ubicación física en la memoria. En su lugar, cada nodo contiene un puntero o referencia que apunta al siguiente nodo de la secuencia.\n\n*   **Nodo:** Contiene el campo de valor (dato) y el enlace al siguiente nodo (*next*).\n*   **Complejidad temporal:** Búsqueda $O(n)$, Inserción al inicio $O(1)$, Eliminación al inicio $O(1)$.\n\n### 1.3 Pilas (Stacks)\nUna pila es una estructura de tipo **LIFO** (Last In, First Out - Último en entrar, primero en salir). Permite almacenar y recuperar datos utilizando únicamente dos operaciones básicas:\n1.  **Push:** Introduce un elemento en el tope de la pila.\n2.  **Pop:** Retira el elemento superior del tope de la pila.");
        
        contenido.setEjercicios("## Guía de Ejercicios Prácticos: Estructuras de Datos Lineales\n\n### Ejercicio 1: Inversión de una Lista Enlazada\n**Instrucciones:** Escribe una función en Python/JavaScript que tome la cabeza (*head*) de una lista enlazada simple y la invierta de forma iterativa, devolviendo la nueva cabeza.\n\n*   **Restricción de Espacio:** $O(1)$ memoria auxiliar.\n*   **Restricción de Tiempo:** $O(n)$ complejidad lineal.\n\n**Solución Guía:**\n```javascript\nfunction invertirLista(head) {\n  let prev = null;\n  let current = head;\n  while (current !== null) {\n    let nextTemp = current.next;\n    current.next = prev;\n    prev = current;\n    current = nextTemp;\n  }\n  return prev;\n}\n```\n\n### Ejercicio 2: El problema de los Paréntesis Balanceados\n**Instrucciones:** Utilizando una pila (*Stack*), diseña un algoritmo para determinar si una cadena de texto que contiene paréntesis `()`, llaves `{}` y corchetes `[]` se encuentra balanceada correctamente.");
        
        contenido.setEvaluacion(java.util.List.of(
            java.util.Map.of(
                "pregunta", "¿Cuál es la complejidad de tiempo para insertar un nodo al inicio de una Lista Enlazada Simple?",
                "opciones", java.util.List.of("O(1) - Tiempo Constante", "O(n) - Tiempo Lineal", "O(log n) - Tiempo Logarítmico", "O(n²) - Tiempo Cuadrático"),
                "opcionCorrectaIndex", 0,
                "explicacion", "Dado que solo se requiere reasignar el puntero 'next' del nuevo nodo al actual 'head' y reasignar el puntero de la cabeza, la operación se realiza en tiempo constante O(1)."
            ),
            java.util.Map.of(
                "pregunta", "¿Qué principio de almacenamiento rige el funcionamiento de una estructura de tipo Pila (Stack)?",
                "opciones", java.util.List.of("FIFO (First In, First Out)", "LIFO (Last In, First Out)", "LILO (Last In, Last Out)", "Random Access"),
                "opcionCorrectaIndex", 1,
                "explicacion", "Las Pilas se rigen bajo el principio LIFO (Last In, First Out), donde el último elemento añadido al tope es el primero en ser extraído."
            ),
            java.util.Map.of(
                "pregunta", "Si realizas una operación 'pop' en una pila que se encuentra vacía, ¿qué término técnico describe este error de desbordamiento?",
                "opciones", java.util.List.of("Stack Overflow", "Stack Underflow", "Null Pointer Exception", "Memory Leak"),
                "opcionCorrectaIndex", 1,
                "explicacion", "El intento de extraer un elemento de una pila sin datos disponibles se denomina técnicamente 'Stack Underflow'."
            )
        ));

        contenido.setDiapositivas(java.util.List.of(
            java.util.Map.of(
                "titulo", "Diapositiva 1: Estructuras Lineales",
                "puntos", java.util.List.of(
                    "Definición básica de estructuras secuenciales.",
                    "Diferencia entre orden físico (arrays) y lógico (listas).",
                    "Importancia de la selección de estructuras en la optimización."
                )
            ),
            java.util.Map.of(
                "titulo", "Diapositiva 2: La Lista Enlazada Simple",
                "puntos", java.util.List.of(
                    "Estructura del Nodo: Dato + Enlace al sucesor.",
                    "Ventaja: Inserciones y eliminaciones ultra rápidas O(1).",
                    "Desventaja: Búsqueda secuencial costosa O(n)."
                )
            ),
            java.util.Map.of(
                "titulo", "Diapositiva 3: Pilas y su Aplicación",
                "puntos", java.util.List.of(
                    "Concepto LIFO (Last In, First Out).",
                    "Operaciones fundamentales: Push y Pop.",
                    "Casos prácticos: Historial del navegador y llamadas recursivas."
                )
            )
        ));
    }

    private ContenidoTemarioResponseDTO mapToDTO(ContenidoTemario entity) {
        return new ContenidoTemarioResponseDTO(
            entity.getId(),
            entity.getTemario().getId(),
            entity.getTeoria(),
            entity.getEjercicios(),
            entity.getEvaluacion(),
            entity.getDiapositivas()
        );
    }
}
