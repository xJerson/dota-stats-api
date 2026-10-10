# DESIGN.md

Reglas de diseño de la interfaz web. Léelas antes de cambiar cualquier cosa visual en `web/`. Están escritas aquí, y no en una skill, para que cualquier agente pueda seguirlas.

Si tu agente soporta skills, carga además `frontend-design` (Claude Code) antes de un cambio visual importante. No sustituye este documento.

## Concepto

El draft como una mesa de guerra entre Radiant (tu equipo) y Dire (los enemigos). Lo memorable es el tablero con las sugerencias al centro; todo lo demás es discreto.

## Colores

Son variables CSS en `src/styles.css`.

| Variable | Valor | Uso |
|---|---|---|
| `--basalt` | `#24201D` | Fondo de la página |
| `--basalt-2` | `#2E2925` | Superficies elevadas |
| `--bone` | `#E9E2D4` | Texto |
| `--bone-dim` | `#A69D8F` | Texto secundario |
| `--radiant` | `#5FA37A` | Tu equipo |
| `--dire` | `#C2452D` | Enemigos. Solo títulos grandes y bordes: no cumple contraste en texto pequeño |
| `--dire-texto` | `#E58A74` | Texto pequeño del lado enemigo |
| `--gold` | `#C9A15A` | Solo las sugerencias y tu propio slot |

## Tipografía

- "Big Shoulders Display": título, nombres de héroe y sugerencias.
- "Figtree": todo lo demás.
- No añadir otras familias.

## Qué evitar

- Etiquetas en mayúsculas con espaciado amplio.
- Resaltar una sola palabra de un título.
- Textos unidos con puntos medios.
- Fuente monoespaciada para datos.
- Flechas "→" en botones.
- Tarjetas idénticas con la misma sombra suave.
- Degradados decorativos.

## Movimiento

Un solo momento orquestado: las sugerencias se asientan al cambiar. Respetar `prefers-reduced-motion`.

## Accesibilidad mínima

- Foco visible en todo.
- Combobox con roles ARIA y teclado completo (flechas, Enter, Escape).
- Texto alternativo en los retratos.
- Contraste AA.
- El estado nunca se indica solo con color.

## Responsive

- Funciona hasta móvil (≤ 760 px), con 16 px de margen lateral y sin scroll horizontal.
- En escritorio, las tres sugerencias deben verse sin scroll a 1366×650.

## Textos

- Español claro, en minúsculas tras la primera letra, con verbos simples.
- Los errores explican qué pasó y cómo arreglarlo; los estados vacíos indican qué hacer.
- Una acción conserva el mismo nombre en todo el flujo.
- Encuadre del producto: el consejo es una ventaja para la fase de línea, no una garantía de ganar.

## Probar de verdad

Tras un cambio visual, revisarlo en un navegador (captura de pantalla) y no solo con tests. Al teclear en las pruebas con navegador, hacer primero un clic real: las primeras teclas tras navegar pueden perderse.
