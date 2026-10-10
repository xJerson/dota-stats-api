# Reglas de habilidades (borrador para revisar)

Estado: borrador. No está conectado al motor de puntaje ni a la API.

## Para qué sirven

El asesor sugiere héroes con datos de OpenDota (meta y duelos). Los duelos entre dos héroes son pocos y ruidosos, así que no capturan cosas como "Hex frena a Storm Spirit aunque se mueva". Estas reglas añaden esa señal a mano: un enemigo (o un rasgo) y un héroe que lo frena bien **en la fase de línea**.

Idea de producto: el consejo es una **ventaja de línea / early game**, no una garantía de victoria. El resto depende de cómo juegues.

## Cómo revisar

1. Lee cada fila y marca en la columna "Revisar": `ok`, `cambiar` o `quitar`.
2. Las filas con confianza "media" son las que más necesitan tu criterio; el motivo de la duda está en las notas bajo la tabla.
3. El "Motivo" es el texto que vería el usuario. Dime si algo suena raro o largo.
4. Los datos de habilidad (nombre interno, nombre mostrado, comportamiento, BKB, dispel) vienen de OpenDota (`/constants/abilities`, `/constants/hero_abilities`, `/constants/heroes`). Los nombres de héroe se validaron contra el catálogo vivo (`/api/draft/heroes`, 127 héroes). No se inventó ningún número ni winrate.

Cómo leer la columna de datos: "BKB: No" significa que el efecto no atraviesa la inmunidad a magia; "BKB: Sí" que sí la atraviesa. "Dispel" es lo que reporta OpenDota para esa habilidad.

## Reglas

| # | Enemigo (héroe o rasgo) | Counter sugerido | Habilidad y dato verificado | Motivo para mostrar al usuario | Posición recomendada | Confianza | Revisar |
|---|---|---|---|---|---|---|---|
| 1 | Storm Spirit (escape por habilidad) | Lion | `lion_voodoo` = "Hex"; Unit Target; BKB: No; dispel: solo fuertes. Descripción: deja al objetivo con "all special abilities disabled" | Hex lo inmoviliza aunque use Ball Lightning. | 4 | alta | |
| 2 | Queen of Pain (escape por habilidad) | Shadow Shaman | `shadow_shaman_voodoo` = "Hex"; Unit Target; BKB: No; dispel: solo fuertes. Descripción: "disabling their attacks and abilities". Escape enemigo: `queenofpain_blink` = "Blink" (Point Target) | Hex le quita el Blink y la deja a tu alcance. | 5 | alta | |
| 3 | Void Spirit (escape por habilidad) | Lion | `lion_voodoo` = "Hex" (ver #1). Escape enemigo: `void_spirit_astral_step` = "Astral Step" (Point Target) | Hex le quita Astral Step y no puede huir. | 4 | media | |
| 4 | Ember Spirit (escape por habilidad) | Skywrath Mage | `skywrath_mage_ancient_seal` = "Ancient Seal"; Unit Target; BKB: No; dispel: Sí; descripción: "silencing it". Escape enemigo: `ember_spirit_fire_remnant` = "Fire Remnant" | Ancient Seal lo silencia y le corta Fire Remnant. | 4 | media | |
| 5 | Puck (escape por habilidad) | Death Prophet | `death_prophet_silence` = "Silence"; AOE, Point Target; radio 450; BKB: No; dispel: Sí. Escape enemigo: `puck_phase_shift` = "Phase Shift" (Channeled) | Silence en área le impide usar Phase Shift. | 2 | media | |
| 6 | Clinkz (invisible) | Slardar | `slardar_amplify_damage` = "Corrosive Haze"; Unit Target; BKB: Sí; dispel: Sí. Descripción: "provides True Sight". Enemigo: `clinkz_wind_walk` = "Skeleton Walk" (invisible) | Corrosive Haze lo revela aunque esté invisible. | 3 | alta | |
| 7 | Riki (invisible) | Bounty Hunter | `bounty_hunter_track` = "Track"; Unit Target (solo héroe); BKB: Sí; dispel: Sí. Descripción: "granting True Sight". Enemigo: `riki_backstab` = "Cloak and Dagger" (invisible hasta atacar) | Track lo revela y puedes perseguirlo. | 4 | alta | |
| 8 | Templar Assassin (invisible) | Slardar | `slardar_amplify_damage` = "Corrosive Haze" (ver #6). Enemigo: `templar_assassin_meld` = "Meld" (invisible mientras está quieta; BKB: Sí) | Corrosive Haze revela a Meld y le baja la armadura. | 3 | media | |
| 9 | Weaver (invisible) | Zeus | `zuus_lightning_bolt` = "Lightning Bolt"; descripción: "True Sight around the target in a 600 radius" y mini-stun. Enemigo: `weaver_shukuchi` = "Shukuchi" ("shifts out of visibility") | Lightning Bolt lo revela y lo frena un instante. | 2 | media | |
| 10 | Phantom Lancer (ilusiones) | Leshrac | `leshrac_pulse_nova` = "Pulse Nova"; No Target; radio 500; daño por segundo en área. Enemigo: `phantom_lancer_juxtapose` = "Juxtapose" (pasiva que crea ilusiones) | Pulse Nova daña a todas sus ilusiones a la vez. | 2 | media | |
| 11 | Naga Siren (ilusiones) | Leshrac | `leshrac_pulse_nova` = "Pulse Nova" (ver #10). Enemigo: `naga_siren_mirror_image` = "Mirror Image" (No Target; dispel básico) | Pulse Nova limpia sus imágenes en área. | 2 | media | |
| 12 | Zeus, Lina (dependen de lanzar hechizos) | Pugna | `pugna_nether_ward` = "Nether Ward"; Point Target; descripción: dispara al héroe enemigo que lanza un hechizo, con daño según el maná gastado | Cada hechizo que lancen se les devuelve en daño. | 2 | media | |
| 13 | Invoker (depende de lanzar y combinar) | Shadow Shaman | `shadow_shaman_voodoo` = "Hex" (ver #2). Enemigo: `invoker_invoke` = "Invoke" (Instant Cast) | Hex le impide lanzar sus hechizos. | 5 | media | |
| 14 | Meepo (depende de Poof) | Lion | `lion_voodoo` = "Hex" (ver #1). Enemigo: `meepo_poof` = "Poof" (Point Target) | Hex le corta Poof y sus clones quedan sin escape. | 4 | media | |
| 15 | Lion, Shadow Shaman (Hex y control en línea) | Abaddon | `abaddon_aphotic_shield` = "Aphotic Shield"; Unit Target; BKB: No; el texto de OpenDota dice "dispelling them" y "DISPEL TYPE: Strong Dispel". Contra: `lion_voodoo` y `shadow_shaman_voodoo` ("Strong Dispels Only") | Aphotic Shield te quita el Hex y te cubre de daño. | 5 | media | |
| 16 | Skywrath Mage, Death Prophet (silencio en línea) | Abaddon | `abaddon_aphotic_shield` = "Aphotic Shield" (ver #15). Contra: `skywrath_mage_ancient_seal` y `death_prophet_silence` (dispel: Sí) | Aphotic Shield te quita el silencio al instante. | 5 | alta | |

### Notas sobre las filas "media"

- **#3, #4, #5**: el efecto es real (Hex o silencio impiden lanzar Astral Step, Fire Remnant y Phase Shift), pero depende de acertar el hechizo antes de que el rival lo use. Son héroes muy reactivos, por eso quedan en media.
- **#8**: Meld es invisibilidad "mientras está quieta"; la revelación sirve, pero Templar Assassin se juega distinto a Clinkz o Riki.
- **#9**: Zeus no es un counter clásico de Weaver; la señal es solo la revelación (Weaver se hace invisible con Shukuchi).
- **#10, #11**: el daño en área ayuda contra ilusiones, pero Leshrac gasta maná con Pulse Nova y la ventaja depende de cómo lo maneje en línea.
- **#12**: Nether Ward castiga el spam de hechizos; los rivales la evitan dejándola sin lanzar, y el valor depende de cuánto spamee el rival.
- **#13, #14**: Hex corta el combo, pero ambos héroes tienen formas de esquivarlo; depende de tener alcance y velocidad.
- **#15**: Abaddon puede limpiar Hex porque OpenDota marca su escudo como "Strong Dispel"; solo funciona si tiene el escudo listo a tiempo.

## Rasgos propuestos

Los rasgos son etiquetas del enemigo; las capacidades son lo que aporta tu héroe. Se asignan a mano y se revisan con el parche. Todos los héroes de las tablas existen en el catálogo vivo.

### Rasgos del enemigo (8)

| Rasgo | Héroes que lo cargan | ¿Regla? |
|---|---|---|
| Escape o movilidad por habilidad | Storm Spirit, Queen of Pain, Ember Spirit, Void Spirit, Puck, Anti-Mage | #1 a #5 |
| Depende del maná | Zeus, Lina, Skywrath Mage, Invoker, Storm Spirit, Leshrac, Keeper of the Light | #12 |
| Invisible | Clinkz, Riki, Weaver, Templar Assassin, Nyx Assassin, Bounty Hunter, Mirana, Sand King | #6 a #9 |
| Basado en ilusiones | Phantom Lancer, Naga Siren, Terrorblade, Chaos Knight, Arc Warden | #10, #11 |
| Depende de lanzar o encadenar hechizos | Invoker, Meepo, Tinker, Rubick | #13, #14 |
| Canaliza (se puede interrumpir) | Crystal Maiden, Enigma, Tinker, Witch Doctor, Puck, Riki | sin regla aún |
| Control en línea con silencio o hex | Lion, Shadow Shaman, Skywrath Mage, Death Prophet, Drow Ranger, Silencer, Disruptor | #15, #16 |
| Muchas unidades invocadas | Lone Druid, Beastmaster, Visage, Undying, Warlock | sin regla aún |

### Capacidades candidatas del counter (8)

| Capacidad | Héroes que la cargan | Dato verificado |
|---|---|---|
| Inmovilización dura que bloquea hechizos | Lion, Shadow Shaman | `lion_voodoo`, `shadow_shaman_voodoo`: "Hex", BKB: No |
| Silencio | Skywrath Mage, Death Prophet, Drow Ranger, Silencer, Disruptor | `skywrath_mage_ancient_seal`, `death_prophet_silence`, `drow_ranger_wave_of_silence` ("Gust"), `disruptor_static_storm`, `silencer_last_word` |
| Drenaje o castigo de maná | Lion, Silencer, Pugna, Anti-Mage | `lion_mana_drain` ("Mana Drain", Channeled), `silencer_glaives_of_wisdom` (roba Inteligencia), `pugna_nether_ward`, `antimage_mana_break` (pasiva que quema maná) |
| Dispel | Abaddon, Legion Commander | `abaddon_aphotic_shield` ("Strong Dispel"), `legion_commander_press_the_attack` (quita debuffs; dispel: Sí) |
| Visión verdadera o revelar | Slardar, Bounty Hunter, Zeus, Drow Ranger | `slardar_amplify_damage`, `bounty_hunter_track`, `zuus_lightning_bolt`, `drow_ranger_wave_of_silence` ("reveals invisible") |
| Daño en área sostenido (contra ilusiones) | Leshrac, Crystal Maiden, Earthshaker | `leshrac_pulse_nova` (radio 500), `crystal_maiden_freezing_field`, `earthshaker_echo_slam` |
| Interrupción corta | Night Stalker, Zeus | `night_stalker_void` (mini-stun de noche), `zuus_lightning_bolt` (mini-stun) |
| Castigo al movimiento | Bloodseeker | `bloodseeker_rupture` ("Rupture": daño si el objetivo se mueve; BKB: Sí) |

## Qué NO cubren estas reglas

- No son una garantía de ganar ni de ganar la línea: son una **ventaja de draft** para la fase de línea. El otro 50% es el jugador.
- Cubren solo la fase de línea. No modelan peleas de equipo, objetos ni el juego tardío.
- Dependen del parche: habilidades, enfriamientos y tipos de dispel cambian. Los datos de OpenDota reflejan el parche actual; las reglas hay que revisarlas en cada parche grande.
- Están hechas a mano y reflejan consenso de la comunidad, no estadística. Las filas "media" lo son por ser más situacionales.
- No consideran talentos, facetas, objetos como BKB o Linken's, ni la posición real de cada jugador en la línea.
- No cubren todos los héroes: solo los casos con interacción clara. Que un héroe no aparezca no significa que no tenga counters.

## Cómo se usarían

Propuesta, sin implementar:

1. Cada regla aporta una **componente extra de puntaje** al héroe sugerido cuando el enemigo (o su rasgo) coincide. No reemplaza el meta ni los duelos; se suma.
2. El **peso es configurable** (por ejemplo `peso-reglas-habilidades`, con 0 para apagarlo), igual que los pesos actuales del motor.
3. Si una regla coincide, su "Motivo" se muestra en la sugerencia (por ejemplo "Hex lo inmoviliza aunque use Ball Lightning"), para que el usuario vea el porqué.
4. Las reglas vivirían en un archivo de datos revisable (enemigo o rasgo, héroe, motivo, confianza). Las de confianza "media" podrían pesar menos o activarse con una opción.
5. Un chequeo automático contra `/constants/abilities` detectaría habilidades que cambien o desaparezcan tras un parche.
