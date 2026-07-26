{katedraContext}
Formula un examen de opción múltiple, en español académico impecable, que
evalúe con rigor la comprensión del tema. El formato de salida es el mismo
en todos los niveles: preguntas con sus opciones y la respuesta correcta
justificada; lo que cambia por nivel es la cantidad y la profundidad.

Adapta el registro al nivel del estudiante:
{nivelRubrica}

Estilo y profundidad obligatorios de este nivel de generación (además del
nivel académico del estudiante, define el enfoque de TODO el examen):
{estiloEvaluacion}

{cantidadInstruccion}

Distribuye las preguntas en niveles cognitivos DISTINTOS y crecientes a lo
largo del examen, repitiendo el ciclo si la cantidad de preguntas lo requiere:
- Recordar o comprender (definiciones e ideas centrales)
- Aplicar (usar el concepto en una situación concreta)
- Analizar (comparar, inferir, interpretar o diagnosticar un caso)

Cada pregunta ofrece cuatro opciones: una correcta y tres distractores
plausibles y no triviales. Los distractores deben representar errores o
confusiones creíbles sobre el tema, nunca opciones absurdas o evidentemente
incorrectas. Está prohibido usar comodines como "todas las anteriores" o
"ninguna de las anteriores".

Cada explicación debe justificar por qué la respuesta correcta lo es y,
brevemente, por qué los distractores principales son incorrectos.

Fundamenta las preguntas en el Contenido fuente proporcionado (la teoría ya
generada para este tema): no inventes conceptos ajenos a ese material. Varía
los enunciados y los enfoques en cada generación para evitar repeticiones.

Nunca uses notación matemática en formato LaTeX (\( \), \[ \], $...$, \frac,
\Theta, etc.): el visor solo interpreta Markdown plano y esa notación se
mostraría como texto roto. Escribe fórmulas y notación de complejidad en texto
plano, por ejemplo O(1), O(n), o <= y >= en vez de sus equivalentes LaTeX.
