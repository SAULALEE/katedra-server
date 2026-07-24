{katedraContext}
Genera EXCLUSIVAMENTE la estructura de un temario escolar sobre el tema
indicado, en formato markdown, en español académico impecable. Un temario es
un índice de contenidos: la lista organizada de unidades, temas y subtemas
que un docente cubriría al enseñar este tema, NO el desarrollo teórico de
esos contenidos.

Adapta el alcance y la profundidad de las unidades al nivel del estudiante:
{nivelRubrica}

REGLAS DE SALIDA (obligatorias):
- Nunca respondas como un asistente conversacional; no incluyas saludos,
  despedidas ni frases introductorias como "Aquí tienes...".
- Nunca desarrolles, expliques ni definas ningún concepto: cada línea es
  únicamente el NOMBRE de una unidad, tema o subtema, sin oraciones
  completas, sin párrafos y sin ejemplos resueltos.
- Prohibido escribir explicaciones, definiciones, párrafos de desarrollo,
  introducciones o conclusiones narrativas. Si un elemento requiere más de
  una línea corta para nombrarse, está mal planteado: divídelo en subtemas.
- Numeración decimal obligatoria de exactamente tres niveles, en texto plano
  (NO uses encabezados `#`/`##` ni viñetas con guiones):
  1. Unidad
  1.1. Tema
  1.1.1. Subtema
  Cada nivel usa un punto como separador y continúa la numeración de forma
  correlativa (1., 2., 3. para unidades; 1.1., 1.2. dentro de la unidad 1;
  1.1.1., 1.1.2. dentro del tema 1.1; y así sucesivamente).
- Genera exactamente {numeroModulos} unidades en total. La cantidad es
  obligatoria: ni una unidad más, ni una menos. Distribuye el alcance del
  tema entre esas {numeroModulos} unidades de forma equilibrada, ajustando
  cuántos temas y subtemas lleva cada una según haga falta para cubrir el
  tema completo sin dejar unidades vacías ni forzar contenido irrelevante.
- Cada unidad debe tener al menos un tema, y cada tema al menos un subtema.
- Organiza las unidades en progresión lógica, de los fundamentos hacia los
  contenidos más avanzados dentro del alcance del tema.
- Usa el contenido fuente proporcionado como referencia principal del
  alcance y el orden; complementa con tu conocimiento académico solo cuando
  la fuente sea insuficiente para cubrir el tema con coherencia.
- El resultado debe poder usarse directamente como el índice de contenidos
  del curso y como guía para desarrollar después la teoría, las evaluaciones
  y las diapositivas de cada tema, sin edición adicional.

Sin relleno, sin repeticiones, sin frases genéricas. No incluyas JSON ni
bloques de código cercados alrededor del contenido.
