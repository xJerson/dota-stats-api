# dota-stats-api

API REST que actúa como **consultor de draft para Dota 2**: le indicas tu rol y los héroes que ya eligieron los enemigos, y te sugiere los 3 mejores picks para la partida según el meta actual, los counters y, opcionalmente, tu propio historial.

También expone estadísticas de jugadores a partir de [OpenDota](https://docs.opendota.com/).

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

### 2. Pedir sugerencias

```http
POST /api/draft/suggest
Content-Type: application/json
```

```json
{
  "miRol": 2,
  "enemigos": { "1": "slark", "2": "sf", "3": null, "4": "cm", "5": "lion" },
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
    "enemigos": { "1": "Slark", "2": "Shadow Fiend", "4": "Crystal Maiden", "5": "Lion" },
    "aliados": {}
  },
  "ambiguos": { "enemigos": {}, "aliados": {} },
  "noEncontrados": { "enemigos": {}, "aliados": {} },
  "sugerencias": [
    { "heroId": 92, "heroe": "Visage", "puntaje": 0.544, "motivo": "55.3% en el meta; fuerte contra Lion (60%)" },
    { "heroId": 62, "heroe": "Bounty Hunter", "puntaje": 0.542, "motivo": "54.5% en el meta; fuerte contra Lion (57%) y Shadow Fiend (57%)" },
    { "heroId": 88, "heroe": "Nyx Assassin", "puntaje": 0.537, "motivo": "52.8% en el meta; fuerte contra Crystal Maiden (73%) y Shadow Fiend (57%)" }
  ],
  "advertencias": []
}
```

- `resueltos`: cómo se interpretó cada abreviatura.
- `ambiguos`: slots cuyo texto coincide con varios héroes; vuelve a enviarlos con más precisión.
- `advertencias`: avisos no fatales, por ejemplo si OpenDota falló al pedir un matchup y ese enemigo se evaluó solo con el meta.

### 3. Administración de datos

| Endpoint | Descripción |
|---|---|
| `GET /api/admin/estado` | Parche cargado, si está obsoleto, matchups en caché y si se usó la caché de disco. |
| `POST /api/admin/refresh` | Descarta la caché (memoria y disco) y recarga. Responde `202`. |

### 4. Estadísticas de jugador

Proxy a los endpoints de jugadores de OpenDota, bajo `/api/jugadores/{steamId}`: `/wl`, `/recentMatches`, `/matches`, `/heroes`, `/peers`, `/totals`, `/counts`, `/ratings`, `/rankings`, `/recomendaciones`, entre otros.

## Cómo se calcula el puntaje

```
meta      = límite inferior de Wilson del winrate del héroe en los brackets configurados
counter   = promedio, por cada enemigo, de (victorias + 100 × meta) / (partidas + 100)
personal  = (tus victorias + 20 × meta) / (tus partidas + 20)          (solo con steamId)

puntaje   = 0.4 × meta + 0.6 × counter                                  (sin steamId)
puntaje   = 0.75 × (0.4 × meta + 0.6 × counter) + 0.25 × personal       (con steamId)
```

Cuando falta información (un duelo sin partidas, un héroe que nunca jugaste), el término vale exactamente el meta del héroe: no da ventaja ni castiga.

Los candidatos se filtran por posición según las etiquetas de rol de OpenDota (por ejemplo, posición 5 → `Support`). Es una heurística configurable.

## Configuración

Todo está en `src/main/resources/application.properties` y documentado ahí. Lo más relevante:

| Propiedad | Por defecto | Descripción |
|---|---|---|
| `dotastats.meta.brackets` | `6,7` | Brackets de OpenDota que se suman (1 = Herald … 7 = Divine, 8 = Immortal). |
| `dotastats.meta.cache-file` | `./data/meta-cache.json` | Caché en disco. Vacío = sin caché de disco. |
| `dotastats.meta.ttl-parche-minutos` | `30` | Cada cuánto se verifica si hay parche nuevo. |
| `dotastats.sugerencias.peso-meta` / `peso-contra` | `0.4` / `0.6` | Pesos del puntaje. |
| `dotastats.sugerencias.peso-personal` | `0.25` | Peso del historial propio cuando se envía `steamId`. |
| `dotastats.sugerencias.roles.N` | ver archivo | Etiquetas de rol aceptadas para la posición N. |

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
├── config/     # Configuración de cliente HTTP, caché y propiedades
└── util/       # Conversión de SteamID
```

## Próximos pasos

- Sinergia con los aliados elegidos.
- Mejor asignación de héroes por posición.
- Interfaz web para cargar el draft.

## Créditos

Datos provistos por [OpenDota](https://www.opendota.com/). Este proyecto no está afiliado a Valve.
