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
| Filas de sugerencias (meta + jugabilidad) | `src/main/java/com/dotastats/analizadores/` (`AnalizadorDraft`, `ContextoDraft`, `ServicioSugerencias`, `AnalizadorMeta`); sumar una fila nueva es una clase `@Component` que implemente `AnalizadorDraft`, sin tocar `ServicioSugerencias` ni el controlador |
| Motor de puntaje (fila "por meta") | `draft/MotorSugerenciasPuntaje.java`, propiedades en `draft/SugerenciasProperties.java`; candidatos por posición extraídos a `draft/CandidatosPorPosicion.java` (la reutiliza también la fila de jugabilidad) |
| Reglas de habilidad (fila "por jugabilidad") | `analizadores/habilidad/` (`ReglaHabilidad`, `RepositorioReglas`, `AnalizadorHabilidad`, `ValidadorReglas`, `ReglasController`); datos en `src/main/resources/reglas-habilidades.json` |
| Dependencias curadas (fila "por jugabilidad") | `analizadores/habilidad/dependencias/` (`RepositorioDependencias`, `TablaContrarrestos`, `TablaPesosEfectoMecanismo`, `FiabilidadHerramienta`); datos en `src/main/resources/dependencias-heroes.json` |
| Archivos del juego (segunda fuente, §5.3) | `src/main/java/com/dotastats/juego/` (`LectorArchivoHeroe`, `IndicadoresHabilidad`, `ArchivosJuegoClient*`); derivación de dependencias y herramientas en `TablaContrarrestos`; lista `movimientoNoEscape` en `dependencias-heroes.json`; URL en `dotastats.juego.url-base` |
| Alias y búsqueda de héroes | `draft/HeroeAliasResolver.java` |
| Carga y caché de datos del meta | `src/main/java/com/dotastats/meta/` (`DatosMetaService`, `CacheDisco`, `Wilson`) |
| Cliente de OpenDota | `client/OpenDotaClient*.java` |
| Admin | `web/AdminController.java` (`POST /api/admin/refresh`, `GET /api/admin/estado`) |
| Interfaz | `web/src/` (`App.tsx`, `components/HeroCombobox.tsx`, `components/Sugerencias.tsx`) |
| Reglas de diseño de la interfaz | `web/DESIGN.md` |
| Configuración y valores por defecto | `src/main/resources/application.properties` (todo está documentado ahí) |
| Reglas de habilidades (borrador original, en español) | `docs/reglas-habilidades-borrador.md` |

## Análisis que debe hacer el proyecto

Cada análisis indica su estado. "Hecho" significa implementado y con tests.

### 1. Meta del héroe (hecho)
- **Pregunta:** ¿qué tan fuerte es este héroe ahora, en mi posición?
- **Cómo:** se parte del límite inferior de Wilson (z = 1,96) del winrate global en los brackets `dotastats.meta.brackets` (por defecto 6 y 7: Ancient y Divine), con datos de `/heroStats`. Para las posiciones 1-3, si hay datos de esa línea (`/scenarios/laneRoles`, con partidas Y victorias) se calcula además el **meta por posición**: `(victoriasLinea + fuerzaPriorLinea × metaGlobal) / (partidasLinea + fuerzaPriorLinea)` (`dotastats.sugerencias.fuerza-prior-linea`, por defecto 200). Sin datos de línea (o en las posiciones 4-5, que no tienen línea mapeada) vale exactamente el meta global. Este meta por posición es el que se usa en el puntaje y como contracción de los duelos.
- **Por qué Wilson:** una muestra chica no debe parecer más fuerte que una grande.
- **Se invalida** cuando cambia el parche (`/constants/patch`).

### 2. Duelos y counters (hecho, con datos débiles)
- **Pregunta:** ¿cómo le va al candidato contra los enemigos ya elegidos?
- **Cómo:** `/heroes/{id}/matchups` de cada enemigo, pedido de forma perezosa y en paralelo (máx. 5), con caché en memoria y en disco. El duelo se contrae hacia el meta por posición: `(victorias + 100 × metaPosicion) / (partidas + 100)`. Sin datos, vale exactamente ese meta.
- **Peso por slot del enemigo:** los rivales directos de la línea pesan 1,0 y el resto 0,5. Rivales directos: posición 1 y 5 contra los slots 3 y 4; posición 2 contra el slot 2; posición 3 y 4 contra los slots 1 y 5.
- **Motivo honesto:** un duelo solo se cita en el motivo como "tendencia" (p. ej. "tendencia: +4% contra Lion (51 partidas)") cuando tiene al menos `dotastats.sugerencias.partidas-minimas-duelo` partidas (por defecto 50) Y su estimación contraída se aleja del meta por posición al menos `dotastats.sugerencias.ventaja-minima-duelo` (por defecto 0,03). Nunca se dice "fuerte contra": una tendencia puede ser positiva o negativa. Si ningún enemigo aporta una tendencia citable, el motivo avisa de que el ranking sale solo del meta, sin ventaja de duelo relevante.
- **Limitación verificada:** OpenDota devuelve muestras diminutas (Anti-Mage contra Outworld Devourer: 7 partidas; en total unas 680 partidas de Anti-Mage frente a más de 580.000 en `/heroStats`). Los duelos son globales: no distinguen línea ni rango. Por eso la mayoría de los motivos no citan ningún duelo. No presentar esos porcentajes como un counter sólido.

### 3. Candidatos por posición (hecho)
- **Posiciones 1 a 3:** por participación en la línea con `/scenarios/laneRoles` (umbral 0,30 y mínimo de 30 partidas); la posición 1 exige además la etiqueta `Carry`.
- **Posiciones 4 y 5:** los datos de línea no separan a los supports, así que se usan listas curadas a mano (`dotastats.sugerencias.heroes-posicion.N`). Si la lista está vacía, se usan las etiquetas de rol de OpenDota (heurística pobre: dejó pasar a Enigma y Night Stalker como soft support).
- **Mantenimiento:** las listas hay que revisarlas en cada parche.

### 4. Historial personal (hecho)
- **Pregunta:** ¿qué tan bien juega el usuario con cada héroe?
- **Cómo:** con `steamId` opcional, `personal = (victorias + 20 × meta) / (partidas + 20)`; `puntaje = 0,75 × base + 0,25 × personal`. Un héroe nunca jugado vale su meta (neutro). El motivo cita el winrate propio desde 5 partidas.
- **Perfil privado:** si `/players/{id}/heroes` viene vacío o con todo en 0 partidas, se responde `422 PERFIL_PRIVADO` con un mensaje claro.

### 5. Reglas de interacción de habilidades (conectado al motor, pendiente de que el usuario revise las 16 reglas)
- **Pregunta:** ¿qué héroe frena bien a este enemigo por cómo funcionan sus habilidades? (ejemplo: Hex de Lion contra Storm Spirit)
- **Estado:** las 16 reglas de `docs/reglas-habilidades-borrador.md` (5 de confianza alta, 11 media) están convertidas a `src/main/resources/reglas-habilidades.json` y conectadas a la fila "Sugerencias por jugabilidad" (`com.dotastats.analizadores.habilidad.AnalizadorHabilidad`, orden 2, después de la fila de meta). El usuario todavía debe marcar cada regla como ok, cambiar o quitar en el borrador; cualquier cambio de criterio se traslada a mano al JSON (ver abajo).
- **Cómo puntúa:** solo cuenta una regla si el enemigo es un rival DIRECTO de `miRol` (misma tabla `oponentesDirectos` del motor de meta: posición 2 contra el slot 2; 1 y 5 contra los slots 3 y 4; 3 y 4 contra los slots 1 y 5) y el héroe `counter` de la regla es candidato de esa posición. Puntos = suma de `pesoConfianza` (alta 1.0, media 0.5, baja 0.25 por defecto, configurable en `dotastats.habilidad.peso-confianza-*`) por cada (regla, rival directo) que coincide. Sin rivales directos elegidos, o sin ninguna coincidencia, la fila sale vacía (no es un error). El motivo es el texto de la regla; una confianza media/baja se antepone con "Posible:" para no leerse como un counter seguro, y si la habilidad del counter (`esUlti`) es su ultimate se aclara "(desde nivel 6)".
- **Dónde viven las reglas:** `src/main/resources/reglas-habilidades.json` (ubicación configurable en `dotastats.habilidad.reglas-archivo`). Cada regla trae `id`, `enemigos` (nombres ya expandidos, no texto libre), `rasgo` (opcional, solo informativo), `counter`, `habilidad`/`habilidadNombre`, `anula` (las claves internas que anula/revela/castiga; vacío si la regla es sobre un rasgo general), `anulaEsUlti`, `esUlti`, `confianza` y `motivo`. `RepositorioReglas` valida los campos obligatorios al arrancar (falla rápido con un mensaje claro) y resuelve los nombres de héroe contra el snapshot de meta vigente (no contra la red, para no bloquear el arranque ni los tests); un nombre que no exista en el catálogo también es un error.
- **Para sumar una regla:** agregar una entrada al JSON con esos campos. No hace falta tocar código.
- **Validación tras un parche:** `GET /api/reglas` lista las reglas cargadas; `GET /api/reglas/validacion` revisa cada `habilidad` y cada `anula` contra `/constants/abilities` y marca cuál ya no existe. Nunca bloquea el arranque, es solo a pedido.
- **Dudas abiertas (heredadas del borrador, pendientes de que el usuario las confirme):** la regla 1 dice "aunque use Ball Lightning" y los datos de habilidad no lo confirman directamente; la 9 (Zeus contra Weaver) es débil. Las marcas de ultimate se revisaron contra `/constants/abilities` y `/constants/hero_abilities` (las ultis tienen 3 niveles de cooldown o maná en vez de 4): `esUlti=true` en las reglas 6 y 8 (Corrosive Haze), 7 (Track) y 10 y 11 (Pulse Nova); `anulaEsUlti=true` en las reglas 1 (Ball Lightning), 3 (Astral Step), 4 (Fire Remnant), 6 (Skeleton Walk) y 10 (Juxtapose). Invoke no se marca: Invoker lo tiene desde el nivel 1. Cloak and Dagger (regla 7) no trae datos de nivel; queda en `false` hasta confirmarlo.

#### 5.1 Interacciones automáticas (cubren lo que las 16 reglas a mano no alcanzan)
- **Por qué:** las reglas a mano son pocas y casi ninguna es consistente por línea (p. ej. jugar posición 2 contra un Storm Spirit en el slot 2 daba la fila vacía). El motor ahora deriva interacciones automáticas de `/constants/abilities` + `/constants/hero_abilities` para cualquier rival directo y candidato que las reglas a mano no cubran, así la fila casi nunca queda vacía en un 1v1 de línea.
- **Alcance:** desde §5.3 esto es solo el RESPALDO: aplica a los enemigos sin dependencias curadas Y sin ninguna dependencia derivada de los archivos del juego (p. ej. mientras esos archivos no llegan, o un héroe sin escape). Los que figuran en `dependencias-heroes.json` usan la tabla curada (§5.2) y los que tienen al menos una dependencia derivada de los archivos del juego usan la tabla (§5.3); ninguno de los dos pasa por la detección por palabra clave.
- **Cómo funciona:** `com.dotastats.analizadores.habilidad.CatalogoHabilidades` arma, por héroe, un `PerfilHabilidadesHeroe` con sus **rasgos** (qué tiene como ENEMIGO: `ESCAPE`, `INVISIBLE`, `ILUSIONES`, `CANALIZA`, `DISABLE_DISPELABLE`) y sus **capacidades** (qué puede hacerle a un enemigo como CANDIDATO: `HEX`, `SILENCIO`, `STUN`, `ROOT`, `TRUE_SIGHT`, `AREA`, `DISPEL_ALIADO`). La detección es por palabra clave/campo (`DetectorHabilidades`, con TODOS los patrones documentados en un solo lugar de esa clase) y `TablaInteraccionesAutomaticas` dice qué capacidad contrarresta qué rasgo y con qué peso relativo (p. ej. Hex pesa más contra un escape que un stun, porque el stun termina y el héroe puede seguir escapando). `AnalizadorHabilidad` combina: por cada rival directo y candidato, junta las coincidencias de las reglas a mano Y las automáticas en una sola lista, suma los puntos y el motivo prioriza las de mayor peso primero; como una regla a mano pesa más, gana el desempate de forma natural (no hay caso especial de "la de mano manda").
- **Dónde ajustar:** los patrones (regex) de cada rasgo/capacidad están todos en `DetectorHabilidades`; la tabla de pesos relativos (qué capacidad cuenta cuánto contra cada rasgo) está en `TablaInteraccionesAutomaticas`; el peso base (antes del multiplicador de la tabla) es `dotastats.habilidad.automatico.factor-peso` (multiplica a `peso-confianza-media`, porque una interacción automática SIEMPRE es confianza "media").
- **Límites conocidos (heurística, no verdad del juego):** las reglas por palabra clave pueden dar falsos positivos o negativos si OpenDota describe algo con palabras distintas a las esperadas (p. ej. un nuke que "charges" energía podría leerse como escape si no se filtra el daño; una habilidad descrita sin la palabra exacta no se detecta). Por eso la confianza automática nunca pasa de "media" ("Posible: ...") y una regla escrita y verificada a mano siempre tiene prioridad real (pesa más) sobre una automática para el mismo candidato. Los datos de habilidad (`/constants/abilities` y `/constants/hero_abilities`) se cachean en memoria y disco junto al resto del snapshot de meta y se descartan con el parche; si OpenDota no responde y no hay caché, la fila sigue funcionando solo con las reglas a mano y se añade una advertencia.

#### 5.2 Dependencias curadas (reemplazan lo automático para los enemigos que figuran en el archivo)
- **Por qué:** el detector por palabra clave daba falsos counters porque el texto de OpenDota se contradice con sus atributos (Inner Fire de Huskar dice "silencing" pero solo trae `disarm_duration`, sin `silence_duration`) y OpenDota no tiene ningún campo que diga de qué TIPO es el efecto de una habilidad (el stun de Earth Spike y el Hex usan la misma clave `duration`). Idea de diseño: **contrarrestar la dependencia, no al héroe**.
- **Qué es:** `dependencias-heroes.json` (ubicación: `dotastats.habilidad.dependencias.archivo`) trae (1) `dependencias`: de qué depende cada enemigo curado (hoy Storm Spirit, Riki, Phantom Lancer, Huskar y Viper; entre 1 y 3 mecanismos: `ESCAPE`, `INVISIBILIDAD`, `ILUSIONES`, `PASIVAS`, `ACOSO_RANGO`, `MANA`) y (2) `herramientas`: las habilidades de los candidatos que atacan esas dependencias, con el **efecto curado a mano** (`HEX`, `SILENCIO`, `STUN`, `ROOT`, `DESARME`, `REVELADO`, `AREA`, `QUEMA_MANA`, `BREAK`) y las claves del atributo de OpenDota de donde salen los NÚMEROS (`claveDuracion`, `claveEvidencia`, `claveRetraso`). Nunca se infiere el efecto de la descripción; solo habilidades (nada de objetos).
- **Qué sale de los datos:** duración, `abilitycastpoint`, retraso y `bkbpierce` a nivel 1. La fórmula de fiabilidad está en un solo lugar (`FiabilidadHerramienta`): base 0,40 + hasta 0,30 por duración (tope 3 s) + bono por tiempo hasta el efecto (instantáneo +0,15) + 0,15 si atraviesa la inmunidad a la magia, y la ultimate se multiplica por 0,5. La tabla de pesos efecto/mecanismo (hex > silencio > stun/root contra un escape; revelar > área contra invisibilidad; área contra ilusiones; quemar maná contra maná; break > desarme contra pasivas) vive en Java (`TablaPesosEfectoMecanismo`). `RANGO_MAYOR` no se cura: se deriva del `attack_range` base de `/constants/heroes` contra la dependencia `ACOSO_RANGO`.
- **Cómo se calcula:** `TablaContrarrestos` se construye UNA vez por snapshot de meta (misma memoización por referencia que `CatalogoHabilidades`, a partir de `DatosMetaService.datosHabilidad()`, que ahora también cachea el alcance de ataque en memoria y disco; una caché vieja sin alcance solo pide `/constants/heroes`). En la petición solo se consulta: cero llamadas a OpenDota. Por cada (enemigo, candidato, mecanismo) cuenta solo la mejor herramienta. Peso = `peso-confianza-*` x peso efecto/mecanismo x fiabilidad.
- **En `AnalizadorHabilidad`:** para un rival directo con dependencias curadas se usa la tabla y **NO** las interacciones automáticas por palabra clave de §5.1 (las 16 reglas a mano siguen aplicando y se suman). Los enemigos sin dependencias curadas usan lo derivado de los archivos del juego (§5.3) si tienen al menos una dependencia derivada y, si no, el comportamiento de §5.1. El motivo cita el camino ("Storm Spirit depende de Ball Lightning (escape): Hex de Lion lo desactiva 2 s en su primer nivel, lanzamiento instantáneo."), con "Posible:" para confianza media/baja y "(desde nivel 6)" para ultimates.
- **Validación:** la estructura del archivo se valida al arrancar; los nombres de héroe contra el snapshot de meta la primera vez que se usan (desconocido = error). Las claves de habilidad y de atributo se validan contra el snapshot de habilidades (nunca contra la red): una clave desconocida, una habilidad que no es del héroe o un atributo inexistente o en 0 a nivel 1 descarta esa entrada y queda en `TablaContrarrestos.problemas()` y en el log (así un parche que renombre algo no rompe toda la fila).
- **Para sumar un enemigo o una herramienta:** agregar la entrada al JSON con claves verificadas en `/constants/abilities` (y confirmar que la clave de duración/evidencia existe y vale más de 0 a nivel 1). Un enemigo nuevo deja de usar lo automático en cuanto figura en `dependencias`.
- **Misma habilidad por regla a mano y por herramienta curada:** si coinciden (mismo enemigo, candidato y habilidad) cuentan una sola vez: se queda el mayor de los dos pesos (no la suma) y solo se muestra el motivo de la regla a mano. Habilidades distintas siguen sumando.
- **Alcance mínimo (`dotastats.habilidad.dependencias.rango-minimo`, por defecto 50):** `RANGO_MAYOR` exige que la ventaja de alcance de ataque base sea mayor o igual a este valor (Silencer 600 contra Viper 575 no califica).
- **Nombre de héroe desconocido en el archivo:** ya no rompe la petición: la entrada se salta, se avisa por log y queda en `TablaContrarrestos.problemas()`. Los errores de estructura o de campos obligatorios siguen fallando al arrancar.
- **Texto de los motivos curados:** los números se dicen "en su primer nivel" (nivel de la habilidad, no del héroe) y, si es ultimate, "(desde nivel 6)" va dentro de la oración, antes del punto final.
- **Mecánicas confirmadas en el juego por el usuario (2026-10-11, con capturas del tooltip):** Ball Lightning "no se puede usar mientras se está inmovilizado" (un root SÍ impide el escape de Storm Spirit) y Storm es invulnerable mientras viaja, así que el counter tiene que actuar ANTES de que lo lance. Viper Strike dura 6 s en todos los niveles (solo sube el daño). La ultimate de Doom NO hace break: disipa efectos positivos, impide usar habilidades y curarse (coincide con `does_break = 0` de OpenDota), por eso no figura como `BREAK`; podría contrarrestar una futura dependencia de curación.

#### 5.3 Segunda fuente: archivos del juego (cubre a los enemigos sin curar)
- **Por qué:** la detección por palabra clave (§5.1) daba falsos counters porque el texto de OpenDota no es fiable y OpenDota no trae ningún campo del tipo de efecto. Los archivos de Valve sí traen banderas por habilidad que OpenDota no tiene.
- **Fuente:** `https://raw.githubusercontent.com/dotabuff/d2vpkr/master/dota/scripts/npc/heroes/<nombre interno>.txt` (un archivo por héroe; el nombre es el `name` de `/heroStats`, p. ej. `npc_dota_hero_lion`), configurable en `dotastats.juego.url-base`. Formato Valve KeyValues (la barra invertida NO es un escape; si un nombre de habilidad se repite se queda el primer bloque: hay 2 repetidos en los 129 archivos, `beastmaster_summon_raptor_attack` y `rattletrap_jetpack_toggle`); una habilidad es cualquier bloque que contenga directamente `AbilityBehavior` (los `special_bonus*` se ignoran). `LectorArchivoHeroe` lo interpreta sin lanzar nunca (un archivo truncado devuelve lo leído) y `ArchivosJuegoClientImpl` los pide con un pool de máximo 5 hilos y los mismos tiempos de espera que OpenDota; un héroe que falla (404, timeout) se registra y se omite sin tumbar la carga.
- **Banderas que se leen** (`IndicadoresHabilidad`): `desactivadaPorRoot` (`AbilityBehavior` con `ROOT_DISABLES`: blinks, Ball Lightning, Doppelwalk...), `disableDuro` (`SpellDispellableType = SPELL_DISPELLABLE_YES_STRONG`: aturdimientos, hexes, lazo, desmembrar; NO distingue stun de hex), `rompible` (`IsBreakable = 1`), `pasiva`, `canalizada`, `oculta` (`HIDDEN`, `NOT_LEARNABLE` o `INNATE_UI`) `objetivoEnemigo` (se lanza contra enemigos, ver abajo) y `ultimate` (`AbilityType = ABILITY_TYPE_ULTIMATE`, añadida para el "(desde nivel 6)" y la fiabilidad de las herramientas derivadas). Medido sobre las 1094 habilidades: ROOT_DISABLES 52, disable duro 58, rompibles 209, canalizadas 34. Silencios, roots, invisibilidad y revelar NO tienen bandera.
- **Cuándo se piden:** el calentamiento (`DatosMetaService.calentarIndicadores`) corre en su PROPIO hilo cuando se construye o se lee el snapshot (arranque, caché de disco, refresco o parche nuevo); NINGUNA petición a `/api/draft/suggest` dispara la descarga. `datosHabilidad()` solo devuelve los indicadores que haya en ese momento y `versionIndicadoresJuego()` cambia cuando llegan, descartan o cambian: `TablaContrarrestos` memoiza por snapshot Y por esa versión, así que si se construyó sin ellos (todavía no llegaron, GitHub caído) se reconstruye una vez cuando llegan, y mientras tanto los enemigos sin curar usan la detección por palabra clave. Reglas: (1) **una sola descarga a la vez** (una llamada con otra en curso vuelve sin descargar; las constantes de OpenDota de `datosHabilidad()` también van de una en una); (2) **plazo global** `dotastats.juego.tiempo-maximo` (60 s): al vencer se conserva lo que llegó y se cancela el resto; (3) **vacío nunca es final**: un resultado vacío no se guarda ni se da por bueno; uno **parcial** se usa en memoria y se persiste tal cual, y como los héroes que faltan son los del snapshot menos los que hay, el siguiente calentamiento (reinicio o refresco) pide SOLO esos; (4) si el snapshot cambió mientras se descargaba (parche, refresco) el resultado viejo se descarta y se repite para el nuevo. Viven en memoria y en la caché de disco dentro del snapshot y se descartan con el parche, igual que el alcance de ataque: una caché vieja sin indicadores se lee igual (no se subió `SnapshotMeta.VERSION`) y solo pide los indicadores. Mientras faltan, la respuesta lleva una advertencia.
- **Dependencias derivadas del enemigo** (solo si NO tiene dependencias curadas; si las tiene, el curado gana entero y lo derivado no se usa): sobre las habilidades PROPIAS del héroe (según `hero_abilities`, no ocultas), `ESCAPE` = `desactivadaPorRoot` salvo las de `movimientoNoEscape`. Confianza siempre `baja`. **`PASIVAS`** (pasivas propias `rompible` y `pasiva`) queda detrás de `dotastats.habilidad.juego.derivar-pasivas`, **apagado por defecto**: casi todos los héroes tienen alguna pasiva rompible, así que las mismas 4 herramientas de break/desarme (Viper Strike, Nyx Vendetta, Inner Fire, Deafening Blast) saldrían repetidas contra todos y taparían a los counters útiles; por defecto solo se deriva el escape.
- **`movimientoNoEscape`** (en `dependencias-heroes.json`): habilidades que Valve marca como no usables enraizado pero que no son un escape (acercarse al enemigo, autodestrucción...). Semilla: `spirit_breaker_charge_of_darkness`, `spirit_breaker_nether_strike`, `techies_suicide`, `phoenix_sun_ray_toggle_move`, `lone_druid_spirit_bear_return`, `necrolyte_death_seeker`. **Pendiente de revisión del usuario.** Una clave desconocida va a `TablaContrarrestos.problemas()`, no rompe nada.
- **Herramientas derivadas del candidato:** toda habilidad propia, no oculta, con `disableDuro` **que se lanza contra enemigos** (`objetivoEnemigo`: `AbilityUnitTargetTeam` incluye `ENEMY`, o no trae equipo y no exige `UNIT_TARGET`: áreas, sin objetivo, pasivas) que no sea ya una herramienta curada (misma clave: gana la curada) entra como efecto `STUN` de confianza `media`, sin clave de duración (usa la rama "sin duración" de `FiabilidadHerramienta`; lanzamiento y `bkbpierce` siguen saliendo de OpenDota). Con esa regla, de los 58 disables duros reales quedan fuera `marci_companion_run` y `snapfire_firesnap_cookie` (solo aliados) y `spirit_breaker_planar_pocket` (ambos equipos), además de las ocultas (`kez_shodo_sai`, `kez_ravens_veil`, `ogre_magi_unrefined_fireblast`, `shadow_shaman_voodoo_hands`, `visage_summon_familiars_stone_form`, `visage_summon_familiars_recall`). Solo contrarresta lo que `STUN` ya contrarresta en `TablaPesosEfectoMecanismo` (hoy, `ESCAPE`). El motivo no inventa cifras: "Posible: Anti-Mage depende de Blink (escape): Earth Spike de Lion lo desactiva (disable duro), lanzamiento de 0,3 s."
- **Política de confianza:** en un camino derivado la confianza es la MENOR entre la de la dependencia y la de la herramienta, y es la que pesa (`peso(confianza) × peso efecto/mecanismo × fiabilidad`). Dependencia derivada (baja) con cualquier herramienta da `baja`; dependencia curada con herramienta derivada da `media`; curada con curada no cambia. Por (enemigo, candidato, mecanismo) solo cuenta la mejor herramienta, y como un héroe puede tener varios escapes derivados también solo cuenta el mejor.
- **Respaldo:** un enemigo cuenta como cubierto por la tabla SOLO si tiene dependencias curadas o al menos UNA dependencia derivada. Si no (sin indicadores, el héroe falló, o tiene indicadores pero ninguna dependencia derivada, como los héroes invisibles o de ilusiones sin escape), conserva la detección por palabra clave (§5.1). Las reglas a mano siguen aplicando y la deduplicación (mismo enemigo, candidato y habilidad cuenta una vez, gana el mayor peso y el motivo de la regla a mano) sigue igual.
- **Sigue curado a mano:** silencio, root, hex, revelar, invisibilidad, quema de maná, break y desarme (el efecto exacto de una herramienta); los archivos del juego solo cubren escape, pasivas rompibles y disable duro. Para precisión en un enemigo concreto, sumarlo a `dependencias` (§5.2).

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
- **Dos filas de sugerencias (decidido por el usuario; hecho en backend e interfaz):** dentro del mismo bloque de resultados, sin héroes repetidos entre filas. `POST /api/draft/suggest` devuelve `filas` (reemplazó al campo `sugerencias`): `[{id, titulo, sugerencias: [{heroId, heroe, motivo, puntaje, confianza}]}]`. Las filas se muestran según `orden()` (meta arriba, jugabilidad abajo), pero un héroe repetido se queda en la fila de menor `prioridadRepetidos()`: jugabilidad gana, porque ahí tiene un motivo concreto, y meta rellena con su siguiente candidato (el motor de meta devuelve el ranking completo y `ServicioSugerencias` recorta cada fila a 3). La web recorre `filas` sin asumir cuántas hay.
  1. **Sugerencias por meta** (`id: "meta"`, orden 1): OpenDota (Wilson + duelos contraídos), sin cambios de comportamiento respecto al motor de siempre. Los duelos se muestran como tendencia, no como counter sólido.
  2. **Sugerencias por jugabilidad** (`id: "habilidad"`, orden 2): reglas de habilidades (§5) contra los rivales directos de la línea. Validación de habilidades contra `/constants/abilities` vía `GET /api/reglas/validacion`.
- Stratz quedó descartado (pide cuenta). OpenDota no da duelos por rango ni por línea con muestra suficiente: el explorer corta por tiempo y `/publicMatches` trae sobre todo Turbo.
- Las reglas de habilidades complementan el meta y los duelos, no los reemplazan. Cada regla es declarativa: rasgo del enemigo, capacidad del counter, rol/fase, confianza y motivo visible. Las de confianza media o baja no se presentan como counters seguros.
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

## Skills y diseño de la interfaz

**Antes de tocar `web/`, lee `web/DESIGN.md`**: tiene las reglas de diseño (colores, tipografía, accesibilidad, responsive y textos). Las skills son una función de Claude Code y otros agentes no pueden invocarlas, por eso las reglas están escritas en ese documento y no en una skill.

### Skills usadas en este proyecto (Claude Code)
- `frontend-design`: se cargó antes de construir la interfaz. Hay que volver a cargarla antes de cualquier cambio visual importante.
- `claude-in-chrome`: para probar la interfaz en un navegador real y sacar capturas.
- Memoria con Engram (`mem_save`, `mem_session_summary`): guardar decisiones, hallazgos y pendientes.

### Skills recomendadas para lo que viene
- `security-review`: para la fase 1 de seguridad.
- `code-review`: antes de publicar.
- `modern-web-guidance`: si se toca la parte web moderna (CSS, accesibilidad, rendimiento).

## Pendientes (en orden)

1. Commitear `docs/reglas-habilidades-borrador.md`, el aviso de ventaja de draft (UI y README) y este archivo (hoy sin commit).
2. Que el usuario revise las 16 reglas de `reglas-habilidades.json` (ya conectadas al motor) (las marcas de ultimate ya se verificaron, ver §5).
3. Adaptar `web/` para consumir `filas` (dos filas: meta y jugabilidad) en vez del campo `sugerencias` que ya no existe en la respuesta.
4. Fase 1 de seguridad, antes de publicar.
5. Mejores datos de duelo y sinergia con aliados (sumarla como un `AnalizadorDraft` nuevo, sin tocar `ServicioSugerencias` ni el controlador).
