# AGENTS.md

Guía para agentes (y personas) que trabajan en este proyecto: qué es, qué análisis debe hacer, dónde vive cada uno, cómo se valida y qué falta.

## Qué es

Consejero de draft para Dota 2. El usuario indica su posición (1 a 5) y los héroes que van eligiendo los enemigos (por slot 1 a 5); la API devuelve las mejores sugerencias con un motivo en español.

**Encuadre de producto (decidido por el usuario):** el consejo es una **ventaja para la fase de línea / early game**, no una garantía de ganar. "El otro 50 % lo pone el jugador." Cualquier texto de interfaz o documentación debe respetarlo.

Los picks de una partida pública no se publican durante el draft, así que el usuario los carga a mano.

## Stack y comandos

- Backend: Java 17, Spring Boot 3.2.5, Maven. Puerto 8080.
- Web: React + TypeScript + Vite en `web/`. Puerto 5173, con proxy de `/api` al backend.
- Datos: solo OpenDota (`https://api.opendota.com/api`, documentación en https://docs.opendota.com/). La key `OPENDOTA_API_KEY` es opcional y va por variable de entorno.

```bash
mvn spring-boot:run        # backend (raíz del repo)
mvn test                   # tests de Java
cd web && npm run dev      # web
cd web && npm run typecheck && npm test && npm run build
```

## Mapa del código

| Zona | Dónde |
|---|---|
| Endpoint y modelo del draft | `src/main/java/com/dotastats/draft/` (`DraftController`, `DraftRequest`, `DraftResponse`) |
| Motor de puntaje | `draft/MotorSugerenciasPuntaje.java`, propiedades en `draft/SugerenciasProperties.java` |
| Alias y búsqueda de héroes | `draft/HeroeAliasResolver.java` |
| Carga y caché de datos del meta | `src/main/java/com/dotastats/meta/` (`DatosMetaService`, `CacheDisco`, `Wilson`) |
| Cliente de OpenDota | `client/OpenDotaClient*.java` |
| Admin | `web/AdminController.java` (`POST /api/admin/refresh`, `GET /api/admin/estado`) |
| Interfaz | `web/src/` (`App.tsx`, `components/HeroCombobox.tsx`, `components/Sugerencias.tsx`) |
| Configuración y valores por defecto | `src/main/resources/application.properties` (todo está documentado ahí) |
| Reglas de habilidades (borrador) | `docs/reglas-habilidades-borrador.md` |

## Análisis que debe hacer el proyecto

Cada análisis indica su estado. "Hecho" significa implementado y con tests.

### 1. Meta del héroe (hecho)
- **Pregunta:** ¿qué tan fuerte es este héroe ahora?
- **Cómo:** límite inferior de Wilson (z = 1,96) del winrate en los brackets `dotastats.meta.brackets` (por defecto 6 y 7: Ancient y Divine), con datos de `/heroStats`.
- **Por qué Wilson:** una muestra chica no debe parecer más fuerte que una grande.
- **Se invalida** cuando cambia el parche (`/constants/patch`).

### 2. Duelos y counters (hecho, con datos débiles)
- **Pregunta:** ¿cómo le va al candidato contra los enemigos ya elegidos?
- **Cómo:** `/heroes/{id}/matchups` de cada enemigo, pedido de forma perezosa y en paralelo (máx. 5), con caché en memoria y en disco. El duelo se contrae hacia el meta: `(victorias + 100 × meta) / (partidas + 100)`. Sin datos, vale exactamente el meta.
- **Peso por slot del enemigo:** los rivales directos de la línea pesan 1,0 y el resto 0,5. Rivales directos: posición 1 y 5 contra los slots 3 y 4; posición 2 contra el slot 2; posición 3 y 4 contra los slots 1 y 5.
- **Limitación verificada:** OpenDota devuelve muestras diminutas (Anti-Mage contra Outworld Devourer: 7 partidas; en total unas 680 partidas de Anti-Mage frente a más de 580.000 en `/heroStats`). Los duelos son globales: no distinguen línea ni rango. Por eso muchos motivos dicen "ranking según el meta, sin datos de duelo suficientes". No presentar esos porcentajes como un counter sólido.

### 3. Candidatos por posición (hecho)
- **Posiciones 1 a 3:** por participación en la línea con `/scenarios/laneRoles` (umbral 0,30 y mínimo de 30 partidas); la posición 1 exige además la etiqueta `Carry`.
- **Posiciones 4 y 5:** los datos de línea no separan a los supports, así que se usan listas curadas a mano (`dotastats.sugerencias.heroes-posicion.N`). Si la lista está vacía, se usan las etiquetas de rol de OpenDota (heurística pobre: dejó pasar a Enigma y Night Stalker como soft support).
- **Mantenimiento:** las listas hay que revisarlas en cada parche.

### 4. Historial personal (hecho)
- **Pregunta:** ¿qué tan bien juega el usuario con cada héroe?
- **Cómo:** con `steamId` opcional, `personal = (victorias + 20 × meta) / (partidas + 20)`; `puntaje = 0,75 × base + 0,25 × personal`. Un héroe nunca jugado vale su meta (neutro). El motivo cita el winrate propio desde 5 partidas.
- **Perfil privado:** si `/players/{id}/heroes` viene vacío o con todo en 0 partidas, se responde `422 PERFIL_PRIVADO` con un mensaje claro.

### 5. Reglas de interacción de habilidades (pendiente de revisión)
- **Pregunta:** ¿qué héroe frena bien a este enemigo por cómo funcionan sus habilidades? (ejemplo: Hex de Lion contra Storm Spirit)
- **Estado:** `docs/reglas-habilidades-borrador.md` tiene 16 reglas (5 de confianza alta, 11 media), con las habilidades verificadas contra `/constants/abilities`. **No está conectado al motor.** El usuario debe marcar cada regla como ok, cambiar o quitar.
- **Plan:** sumar un componente de puntaje con peso configurable (0 = apagado), mostrar el motivo de la regla y validar tras cada parche que las habilidades sigan existiendo. Cubre solo la fase de línea.
- **Dudas abiertas:** la regla 1 dice "aunque use Ball Lightning" y los datos de habilidad no lo confirman; la 9 (Zeus contra Weaver) es débil.

### 6. Sinergia con aliados (no implementado)
El motor recibe los aliados, pero solo los excluye de los candidatos. OpenDota no da sinergias listas; habría que calcularlas con otra fuente.

### 7. Calidad de los datos (continuo)
Antes de afirmar algo sobre un matchup o un winrate, comprobar el tamaño de la muestra. Revisar si los datos están viejos (`GET /api/admin/estado`, campo `obsoleto`). Nombres de héroe: la fuente canónica es `/heroStats` (`/heroes` trae nombres viejos, como "Outworld Destroyer" y "Ringmaster").

### 8. Seguridad y abuso (pendiente, antes de publicar)
El usuario piensa publicar la app para pocas personas. El riesgo principal no es una intrusión sino el **abuso que agote la cuota de OpenDota**. Fase 1:
- Proteger `/api/admin/**` con un token (`X-Admin-Token` desde variable de entorno) o no exponerlo.
- Rate limiting por IP (más estricto con `steamId`).
- Límites de tamaño: nombres de héroe de hasta 40 caracteres, cuerpo de hasta 4 KB.
- Cerrar o filtrar con lista blanca el proxy `/api/jugadores/{id}/*` (hoy reenvía cualquier parámetro y `POST .../refresh` está abierto).
- Actualizar Spring Boot 3.2.5 (sin soporte) y correr `npm audit` en `web/`.
- CORS solo para el dominio real (`dotastats.web.cors-origins`), HTTPS detrás de un proxy y cabeceras de seguridad.
- Detectar con logs estructurados y alertas por muchos 429 propios o 503 de OpenDota. Si algo pasa: bloquear la IP en el proxy, apoyarse en la caché de disco y borrar `data/meta-cache.json` si se corrompe.

### 9. Producto e interfaz (continuo)
- Aviso visible: "Es una ventaja para la fase de línea. El resto depende de cómo juegues."
- Idea en evaluación: separar el panel en **por meta** y **por rol/draft**, con 5 sugerencias cada uno y sin repetidos. El usuario aún no confirmó el diseño exacto.
- Descartado por ahora: Stratz (requeriría token e inicio de sesión con Steam; el usuario no lo quiere).

## Reglas de trabajo para agentes

- **TDD estricto:** escribir el test, verlo fallar y recién después implementar.
- **Verificar, no suponer:** no afirmar nada técnico sin comprobarlo. Volver a correr los tests (`mvn test`, `npm test`) en vez de confiar en el resumen de otro agente: los reportes ajenos ya tuvieron errores de conteo.
- **No hacer commit ni push** sin que el usuario lo pida. Commits convencionales y **sin** línea de coautoría.
- **Estilo:** identificadores, comentarios y textos en español, como el código existente. En la interfaz, español claro, en minúsculas tras la primera letra, con verbos simples.
- **OpenDota con cortesía:** pocas peticiones, caché primero. El cliente de Python `urllib` recibe `403`; usar `curl` o el cliente de Java. Una prueba de humo no debe superar unas 10 llamadas reales.
- **Los tests no tocan la red.** Todo OpenDota va con mocks y los archivos temporales, en `@TempDir`.
- **Pruebas en el navegador:** teclear solo después de un clic real; las primeras teclas tras navegar pueden perderse con la herramienta de prueba.
- **Cambios de parche:** tras un parche grande, revisar las listas curadas de las posiciones 4 y 5 y las reglas de habilidades.

## Pendientes (en orden)

1. Commitear `docs/reglas-habilidades-borrador.md`, el aviso de ventaja de draft (UI y README) y este archivo (hoy sin commit).
2. Que el usuario revise las 16 reglas y, con su visto bueno, conectarlas al motor.
3. Fase 1 de seguridad, antes de publicar.
4. Decidir y construir el panel doble (meta y rol).
5. Mejores datos de duelo y sinergia con aliados.
