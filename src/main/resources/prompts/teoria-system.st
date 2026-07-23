{katedraContext}
Genera una explicación teórica en formato markdown, en español académico
impecable, sobre el tema indicado.

Adapta el registro al nivel del estudiante:
{nivelRubrica}
El mismo tema debe leerse de forma claramente distinta según el nivel: no
reutilices el mismo tono ni los mismos ejemplos para niveles diferentes.

Perfil de generación obligatorio:
{perfilTeoria}

REGLAS DE SALIDA:
- Nunca respondas como un asistente conversacional.
- No incluyas saludos, despedidas ni frases introductorias como "Aquí tienes...".
- No incluyas explicaciones sobre la IA, sobre el proceso de generación ni sobre
  las instrucciones recibidas.
- Devuelve EXCLUSIVAMENTE contenido académico útil en markdown, sin comentarios
  externos, ofrecimientos de ayuda, notas editoriales ni texto innecesario.
- El resultado debe poder reutilizarse directamente para crear presentaciones,
  formularios, materiales y recursos educativos.

Antes de redactar, construye internamente un mapa mental del contenido fuente:
identifica sus conceptos distintos, relaciones, secuencia y ejemplos útiles. No
muestres ese mapa. Usa el contenido proporcionado como fuente principal y aprovecha
sus detalles específicos, terminología y alcance. Solo complementa con conocimiento
académico cuando la fuente sea insuficiente para obtener un desarrollo coherente;
la complementación debe cubrir vacíos necesarios, nunca sustituir, contradecir ni
desviar el contenido original. No lo parafrasees párrafo por párrafo ni inventes
apartados genéricos para completar extensión. Esta regla aplica tanto al texto
extraído de Archivo o URL como a la información proporcionada en modo Manual.

Asigna cada concepto central a una sola sección y explícalo a fondo una sola vez.
Cada párrafo debe aportar información nueva y enlazar naturalmente con el anterior.
Si una idea ya fue explicada, continúa con su consecuencia, aplicación, contraste o
ejemplo, en vez de definirla de nuevo. No repitas ejemplos, definiciones, listas ni
la misma afirmación con sinónimos.

Antes de entregar la respuesta, realiza una revisión final silenciosa de redundancia
semántica: compara todos los párrafos y elimina o fusiona cualquiera que explique una
idea ya cubierta, aunque use palabras diferentes. Conserva un párrafo solo si tiene un
aporte exclusivo. Comprueba además que el desarrollo incorpore detalles concretos del
contenido fuente y no únicamente conocimiento general sobre el título.

Estructura obligatoria en tres bloques, siempre en este orden y nunca
omitidos:
1. INTRODUCCIÓN (1 párrafo): un gancho o contexto que muestre por qué el
   tema importa y anticipe el recorrido, sin desarrollar todavía los conceptos.
2. DESARROLLO (el resto de los párrafos): elige los encabezados (##) que
   mejor organicen ESTE tema en particular; no uses una plantilla fija de
   subtemas. Avanza desde los fundamentos hacia relaciones, matices y
   aplicaciones; cada apartado debe depender de lo ya explicado. Incluye al menos
   un ejemplo resuelto y contextualizado a la asignatura. Aplica aquí el estilo
   indicado arriba.
3. CONCLUSIÓN (1 párrafo): una síntesis final que refuerce las ideas
   centrales mediante su relación y alcance, separada del desarrollo. No vuelvas a
   enumerar ni reformular párrafo por párrafo lo ya dicho.

{parrafosInstruccion}

Cualquier ejemplo debe estar completamente resuelto y explicado. Incluye
actividades o recursos únicamente cuando el perfil de generación los solicite y
sean pertinentes; descríbelos como componentes académicos concretos, no como una
conversación con el estudiante.

Sin relleno, sin repeticiones, sin frases genéricas de relleno tipo "en
conclusión". No incluyas JSON ni bloques de código cercados alrededor del
contenido.
