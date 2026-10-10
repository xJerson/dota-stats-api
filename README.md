# dota-stats-api

API REST que actúa como **consultor de draft para Dota 2**: le indicas tu rol y los héroes que ya eligieron los enemigos, y te sugiere los 3 mejores picks para la partida según el meta actual, los counters y, opcionalmente, tu propio historial.

También expone estadísticas de jugadores a partir de [OpenDota](https://docs.opendota.com/).

> El consejo es una ventaja para la fase de línea (el inicio de la partida), no una garantía de victoria: el resto depende de cómo juegues.

> Durante el draft, Valve no publica los picks de partidas de matchmaking. Por eso el draft lo ingresa el usuario.

## Características

- **Sugerencias por rol**: top 3 de héroes para tu posición (1 a 5), excluyendo los ya elegidos.
- **Meta + counters**: winrate del héroe en el bracket configurado y su desempeño contra cada enemigo elegido.
- **Corrección por muestra chica**: intervalo de Wilson y contracción hacia el meta. Un 2 de 2 no cuenta como 100 %.
- **Abreviaturas de héroes**: `sf`, `am`, `cm`, `qop`, prefijos y errores de tipeo (`slrk` → Slark). Si hay ambigüedad, devuelve los candidatos.
- **Personalización opcional**: con `steamId`, suma tu winrate por héroe sin castigar a los que nunca jugaste.
- **Pocas llamadas a OpenDota**: al arrancar solo pide `/heroStats` y el parche; los matchups se piden por enemigo, en paralelo, y quedan en caché en memoria y en disco.
- **Detección de parche**: si sale un parche nuevo, la caché se descarta y se recarga sola.

## Requisitos

- Java 17
- Maven 3.9+

## Cómo ejecutar

```bash
mvn spring-boot:run
```

La API queda en `http://localhost:8080`.

### API key de OpenDota (opcional)

Sin key, OpenDota aplica un límite de tasa bajo compartido por IP. Para subirlo, obtén una key en <https://www.opendota.com/api-keys> y pásala por variable de entorno:

```bash
export OPENDOTA_API_KEY=tu_key        # Linux / macOS
$env:OPENDOTA_API_KEY="tu_key"        # PowerShell
```

## Uso

### 1. Ver los roles

```http
GET /api/draft/roles
```

| Número | Rol |
|---|---|
| 1 | Carry |
| 2 | Mid |
| 3 | Offlane |
| 4 | Soft support |
| 5 | Hard support |

### 2. Listar los héroes

```http
GET /api/draft/heroes
```

Devuelve el catálogo ordenado por nombre. Usa el catálogo de OpenDota ya cacheado, así que no hace llamadas extra.

```json
[
  {
    "id": 11,
    "nombre": "Shadow Fiend",
    "img": "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/nevermore.png",
    "icono": "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/nevermore.png",
    "roles": ["Carry", "Nuker"],
    "alias": ["sf"]
  }
]
```

| Campo | Descripción |
|---|---|
| `img` / `icono` | Retrato y miniatura (URL absoluta). Pueden ser `null` si el catálogo no trae el nombre interno. |
| `alias` | Abreviaturas integradas que resuelven a ese héroe (las mismas que acepta `/suggest`). |

### 3. Pedir sugerencias

```http
POST /api/draft/suggest
Content-Type: application/json
```

```json
{
  "miRol": 2,
  "enemigos": { "1": "slark", "2": "sf" },
  "aliados": {},
  "steamId": "opcional"
}
```

| Campo | Descripción |
|---|---|
| `miRol` | Tu posición, de 1 a 5. Obligatorio. |
| `enemigos` | Slot → héroe. `null` o ausente = todavía sin elegir. Puedes enviar solo los que ya se conocen. |
| `aliados` | Igual que `enemigos`, para tu equipo. |
| `steamId` | Opcional. SteamID64 o account_id para personalizar con tu historial. |

Respuesta:

```json
{
  "miRol": 2,
  "resueltos": {
    "enemigos": { "1": "Slark", "2": "Shadow Fiend" },
    "aliados": {}
  },
  "ambiguos": { "enemigos": {}, "aliados": {} },
  "noEncontrados": { "enemigos": {}, "aliados": {} },
  "sugerencias": [
    { "heroId": 76, "heroe": "Outworld Devourer", "puntaje": 0.535, "motivo": "54.4% en el meta; fuerte contra Slark (56%)" },
    { "heroId": 82, "heroe": "Meepo", "puntaje": 0.527, "motivo": "53.6% en el meta; fuerte contra Shadow Fiend (59%)" },
    { "heroId": 32, "heroe": "Riki", "puntaje": 0.522, "motivo": "53.2% en el meta" }
  ],
  "advertencias": []
}
```

- `resueltos`: cómo se interpretó cada abreviatura.
- `ambiguos`: slots cuyo texto coincide con varios héroes; vuelve a enviarlos con más precisión.
- `advertencias`: avisos no fatales, por ejemplo si OpenDota falló al pedir un matchup y ese enemigo se evaluó solo con el meta.

### 4. Administración de datos

| Endpoint | Descripción |
|---|---|
| `GET /api/admin/estado` | Parche cargado, si está obsoleto, matchups en caché y si se usó la caché de disco. |
| `POST /api/admin/refresh` | Descarta la caché (memoria y disco) y recarga. Responde `202`. |

### 5. Estadísticas de jugador

Proxy a los endpoints de jugadores de OpenDota, bajo `/api/jugadores/{steamId}`: `/wl`, `/recentMatches`, `/matches`, `/heroes`, `/peers`, `/totals`, `/counts`, `/ratings`, `/rankings`, `/recomendaciones`, entre otros.

## Interfaz web

La carpeta `web/` contiene una interfaz en React + TypeScript + Vite para cargar el draft: tablero con Dire (enemigos) y Radiant (tu equipo), selector de rol, Steam ID opcional y las 3 sugerencias al centro.

Requisitos: Node 20 y npm 10.

```bash
# Terminal 1: la API en http://localhost:8080
mvn spring-boot:run

# Terminal 2: la interfaz en http://localhost:5173
cd web
npm install
npm run dev
```

En desarrollo, Vite reenvía `/api` a `http://localhost:8080`, así que no hace falta configurar CORS. Si sirves la interfaz desde otro origen, agrégalo en `dotastats.web.cors-origins` (lista separada por comas; por defecto `http://localhost:5173`).

| Comando (dentro de `web/`) | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo con recarga. |
| `npm run build` | Revisa tipos y genera `web/dist`. |
| `npm run typecheck` | Solo revisa tipos. |
| `npm test` | Tests con Vitest y Testing Library. |

## Cómo se calcula el puntaje

```
meta      = límite inferior de Wilson del winrate del héroe en los brackets configurados
counter   = promedio PONDERADO, por cada enemigo, de (victorias + 100 × meta) / (partidas + 100)
            peso del enemigo = 1.0 si juega contra tu línea, 0.5 si no (según el slot en que lo pusiste)
personal  = (tus victorias + 20 × meta) / (tus partidas + 20)          (solo con steamId)

puntaje   = 0.4 × meta + 0.6 × counter                                  (sin steamId)
puntaje   = 0.75 × (0.4 × meta + 0.6 × counter) + 0.25 × personal       (con steamId)
```

Cuando falta información (un duelo sin partidas, un héroe que nunca jugaste), el término vale exactamente el meta del héroe: no da ventaja ni castiga.

### Qué enemigos pesan más

El slot (1-5) en que escribes a cada enemigo importa: el motor lo recibe por posición, no como una lista suelta. Se usan las líneas estándar (la línea segura, posiciones 1 y 5, se enfrenta a la ofensiva rival, posiciones 3 y 4; la central contra la central). Un enemigo que juega contra tu línea pesa `peso-oponente-directo` (1.0 por defecto) en el promedio del counter; los demás pesan `peso-otros-enemigos` (0.5). El promedio se normaliza por la suma de pesos.

| Tu posición | Enemigos que juegan contra tu línea (slots) |
|---|---|
| 1 (carry) | 3 y 4 |
| 2 (mid) | 2 |
| 3 (offlane) | 1 y 5 |
| 4 (soft support) | 1 y 5 |
| 5 (hard support) | 3 y 4 |

Los motivos son honestos con la muestra:

- `fuerte contra X (NN%)` solo aparece si el duelo tiene al menos `min-partidas-duelo` partidas (10 por defecto).
- Si un enemigo que juega contra tu línea tiene menos partidas (o ninguna), el motivo añade `sin datos suficientes contra X` (hasta 2 nombres). Ese enemigo cuenta con el meta del héroe, sin ventaja ni castigo.
- Si ningún enemigo tiene datos suficientes, el motivo dice `ranking según el meta, sin datos de duelo suficientes`: el orden sale solo del meta.

Los datos de duelo son globales (el winrate de un héroe contra otro en todas las líneas), no por línea, y muchas veces son una muestra pequeña. Por eso el peso por slot afina el promedio pero no convierte el duelo en un dato de línea.

### Qué héroes son candidatos a cada posición

- **Posiciones 1, 2 y 3** usan datos reales de línea de OpenDota (`/scenarios/laneRoles`: 1 = línea segura, 2 = central, 3 = ofensiva). Un héroe es candidato a la posición N si su participación en la línea N (`partidas en la línea N / partidas en las líneas 1-3`) es al menos `umbral-linea` (30 % por defecto) **y** tiene al menos `min-partidas-linea` partidas (30) en ella. La posición 1 exige además la etiqueta `Carry`, porque los soportes también juegan la línea segura. Así un héroe etiquetado `Carry` que casi siempre juega la línea segura ya no aparece como mid.
- **Posiciones 4 y 5** usan una **lista curada a mano** (`dotastats.sugerencias.heroes-posicion.4` y `.5`, nombres separados por coma, sin distinguir mayúsculas ni acentos): los candidatos son solo esos héroes, sin exigir etiqueta. Las etiquetas de OpenDota son demasiado laxas (`Support`/`Initiator` dejaban pasar a Enigma o Night Stalker como soft supports) y los datos de línea no distinguen a un soporte de un núcleo que juega la misma línea. Las listas son editables y **conviene revisarlas después de cada parche**; un nombre que no exista en el catálogo se avisa con un `WARN` en el log al cargar los datos de meta (no falla). Si una posición no tiene lista (o está vacía) se usa el respaldo por etiquetas de `roles.N` (`Support`, y `Initiator` para la 4).
- **Sin datos de línea** (OpenDota falló al cargarlas) las posiciones 1-3 recurren a las etiquetas y la respuesta incluye una entrada en `advertencias`.
- `dotastats.sugerencias.excluir-posicion.N` permite quitar a mano héroes concretos de una posición.

Los datos de línea se piden junto con `/heroStats` (3 peticiones más), se guardan en la caché en disco y se descartan con el parche, igual que el resto del meta.

## Configuración

Todo está en `src/main/resources/application.properties` y documentado ahí. Lo más relevante:

| Propiedad | Por defecto | Descripción |
|---|---|---|
| `dotastats.meta.brackets` | `6,7` | Brackets de OpenDota que se suman (1 = Herald … 7 = Divine, 8 = Immortal). |
| `dotastats.web.cors-origins` | `http://localhost:5173` | Orígenes con permiso CORS sobre `/api/**`, separados por coma. |
| `dotastats.meta.cache-file` | `./data/meta-cache.json` | Caché en disco. Vacío = sin caché de disco. |
| `dotastats.meta.ttl-parche-minutos` | `30` | Cada cuánto se verifica si hay parche nuevo. |
| `dotastats.sugerencias.peso-meta` / `peso-contra` | `0.4` / `0.6` | Pesos del puntaje. |
| `dotastats.sugerencias.peso-oponente-directo` / `peso-otros-enemigos` | `1.0` / `0.5` | Peso en el counter de los enemigos que juegan contra tu línea y de los demás. Ambos >= 0 y no los dos en cero. |
| `dotastats.sugerencias.peso-personal` | `0.25` | Peso del historial propio cuando se envía `steamId`. |
| `dotastats.sugerencias.umbral-linea` | `0.30` | Posiciones 1-3: participación mínima del héroe en la línea de la posición (sobre sus partidas en las líneas 1-3). |
| `dotastats.sugerencias.min-partidas-linea` | `30` | Posiciones 1-3: partidas mínimas del héroe en esa línea. |
| `dotastats.sugerencias.heroes-posicion.N` | listas de 4 y 5 | Lista curada de héroes candidatos a la posición N (anula las etiquetas de esa posición). Revisar tras cada parche. |
| `dotastats.sugerencias.roles.N` | ver archivo | Etiquetas de rol aceptadas para la posición N cuando no hay lista curada (y en 1-3 sin datos de línea). |
| `dotastats.sugerencias.excluir-posicion.N` | vacío | Nombres de héroe (separados por coma) que nunca se sugieren en la posición N. |

## Errores

Todas las respuestas de error usan el mismo formato:

```json
{ "error": "PERFIL_PRIVADO", "mensaje": "Texto legible para el usuario" }
```

| Código HTTP | `error` | Cuándo |
|---|---|---|
| 400 | `SOLICITUD_INVALIDA` | Body mal formado, `miRol` o slot fuera de 1–5. |
| 400 | `HEROE_DUPLICADO` | El mismo héroe aparece dos veces en el draft. |
| 400 | `STEAM_ID_INVALIDO` | El `steamId` no tiene un formato válido. |
| 404 | `JUGADOR_NO_ENCONTRADO` | El jugador no existe en OpenDota. |
| 422 | `PERFIL_PRIVADO` | El perfil no expone sus partidas. Actívalo en Dota 2 ("Exponer datos públicos de partidas") y vuelve a intentar. |
| 503 | `DATOS_NO_CARGADOS` | Los datos del meta todavía se están cargando. |
| 503 | `OPENDOTA_NO_DISPONIBLE` | OpenDota no respondió. |

## Tests

```bash
mvn test
```

Los tests no hacen llamadas de red: OpenDota se simula con mocks.

## Estructura

```
src/main/java/com/dotastats/
├── draft/      # Consultor de draft: controller, resolución de alias, motor de puntaje
├── meta/       # Datos del meta: carga perezosa, caché en disco, Wilson
├── client/     # Cliente de OpenDota
├── web/        # Controllers de jugador y admin, manejo de errores
├── service/    # Recomendaciones sobre el rendimiento del jugador
├── config/     # Configuración de cliente HTTP, caché, CORS y propiedades
└── util/       # Conversión de SteamID

web/            # Interfaz web (React + TypeScript + Vite)
```

## Próximos pasos

- Sinergia con los aliados elegidos.
- Mejor asignación de héroes por posición.

## Créditos

Datos provistos por [OpenDota](https://www.opendota.com/). Este proyecto no está afiliado a Valve.
