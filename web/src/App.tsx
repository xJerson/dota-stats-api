import { useCallback, useEffect, useMemo, useState } from "react";
import { obtenerHeroes, obtenerRoles } from "./api";
import { HeroSlot } from "./components/HeroSlot";
import { Sugerencias } from "./components/Sugerencias";
import {
  ID_SLOT_PROPIO,
  POSICIONES,
  acercarSiHaceFalta,
  elegidos,
  enfocarSinSaltar,
  idCambiar,
  idSlot,
  nombreCorto,
  ponerHeroe,
  siguienteVacio,
} from "./draft";
import type { Equipo, Hero, Rol, Slots, Sugerencia } from "./types";
import { useSugerencias } from "./useSugerencias";

const CLAVE_STEAM = "dota-draft.steamId";

const ROLES_RESPALDO: Rol[] = [
  { numero: 1, nombre: "Carry" },
  { numero: 2, nombre: "Mid" },
  { numero: 3, nombre: "Offlane" },
  { numero: 4, nombre: "Soft support" },
  { numero: 5, nombre: "Hard support" },
];

function leerSteamId(): string {
  try {
    return localStorage.getItem(CLAVE_STEAM) ?? "";
  } catch {
    return "";
  }
}

function guardarSteamId(valor: string) {
  try {
    if (valor) localStorage.setItem(CLAVE_STEAM, valor);
    else localStorage.removeItem(CLAVE_STEAM);
  } catch {
    /* almacenamiento no disponible: se sigue sin persistir */
  }
}

export default function App() {
  const [roles, setRoles] = useState<Rol[]>(ROLES_RESPALDO);
  const [heroes, setHeroes] = useState<Hero[]>([]);
  const [errorHeroes, setErrorHeroes] = useState(false);
  const [cargandoHeroes, setCargandoHeroes] = useState(true);
  const [cargaHeroes, setCargaHeroes] = useState(0);
  const [rol, setRol] = useState(2);
  const [enemigos, setEnemigos] = useState<Slots>({});
  const [aliados, setAliados] = useState<Slots>({});
  const [steamId, setSteamId] = useState(leerSteamId);
  const [foco, setFoco] = useState<string | null>(null);
  // Pick propio: solo en el cliente. Nunca entra en `aliados`, así /suggest no se entera y la lista no se reordena.
  const [miPick, setMiPick] = useState<Hero | null>(null);
  const [anuncio, setAnuncio] = useState("");

  useEffect(() => {
    const control = new AbortController();
    obtenerRoles(control.signal)
      .then((r) => r.length > 0 && setRoles(r))
      .catch(() => undefined);
    return () => control.abort();
  }, []);

  useEffect(() => {
    const control = new AbortController();
    setErrorHeroes(false);
    setCargandoHeroes(true);
    obtenerHeroes(control.signal)
      .then((lista) => {
        setHeroes(lista);
        setCargandoHeroes(false);
      })
      .catch(() => {
        if (control.signal.aborted) return;
        setErrorHeroes(true);
        setCargandoHeroes(false);
      });
    return () => control.abort();
  }, [cargaHeroes]);

  useEffect(() => guardarSteamId(steamId.trim()), [steamId]);

  useEffect(() => {
    if (!foco) return;
    enfocarSinSaltar(document.getElementById(foco));
    setFoco(null);
  }, [foco, enemigos, aliados]);

  const { estado, reintentar } = useSugerencias(
    rol,
    enemigos,
    aliados,
    steamId,
  );

  const disponibles = useMemo(() => {
    const usados = elegidos(enemigos, aliados);
    if (miPick) usados.add(miPick.id);
    return heroes.filter((h) => !usados.has(h.id));
  }, [heroes, enemigos, aliados, miPick]);

  const nombrePosicion = useCallback(
    (pos: number) =>
      nombreCorto(
        roles.find((r) => r.numero === pos)?.nombre ?? `Posición ${pos}`,
      ),
    [roles],
  );

  function elegir(equipo: Equipo, pos: number, hero: Hero) {
    const slots = equipo === "enemigos" ? enemigos : aliados;
    const nuevos = ponerHeroe(slots, pos, hero);
    (equipo === "enemigos" ? setEnemigos : setAliados)(nuevos);
    const omitir = equipo === "aliados" ? rol : undefined;
    const siguiente = siguienteVacio(nuevos, pos, omitir);
    // sigue en el siguiente slot vacío; si no queda ninguno, queda en "Cambiar" del slot recién llenado
    setFoco(siguiente ? idSlot(equipo, siguiente) : idCambiar(equipo, pos));
  }

  function quitar(equipo: Equipo, pos: number) {
    (equipo === "enemigos" ? setEnemigos : setAliados)((s) =>
      ponerHeroe(s, pos, null),
    );
    setFoco(idSlot(equipo, pos));
  }

  function elegirPropio(s: Sugerencia) {
    // El nombre es el de la sugerencia (fuente canónica: el mismo que ve el usuario en la lista); el catálogo
    // solo aporta imagen e icono.
    const delCatalogo = heroes.find((h) => h.id === s.heroId);
    const hero: Hero = {
      id: s.heroId,
      img: null,
      icono: null,
      roles: [],
      alias: [],
      ...delCatalogo,
      nombre: s.heroe,
    };
    setMiPick(hero);
    setAnuncio(
      `Elegiste ${hero.nombre} para tu slot de ${nombrePosicion(rol)}`,
    );
    // el foco se queda en la sugerencia; si el slot propio queda fuera de la ventana, se acerca lo mínimo
    requestAnimationFrame(() =>
      acercarSiHaceFalta(document.getElementById(ID_SLOT_PROPIO)),
    );
  }

  function enfocarSugerencias() {
    enfocarSinSaltar(document.querySelector<HTMLElement>(".sugerencia__boton"));
  }

  function quitarPropio() {
    setMiPick(null);
    setAnuncio("");
    enfocarSugerencias();
  }

  function cambiarRol(nuevo: number) {
    setRol(nuevo);
    setMiPick(null);
    setAnuncio("");
    // el slot propio no lleva héroe: lo elegido ahí se descarta
    setAliados((s) => ponerHeroe(s, nuevo, null));
  }

  function reiniciar() {
    setEnemigos({});
    setAliados({});
    setMiPick(null);
    setAnuncio("");
    setFoco(idSlot("enemigos", 1));
  }

  const errorSteam =
    estado.fase === "error" && estado.error?.zona === "steam"
      ? estado.error.texto
      : null;
  const hayDraft =
    Object.keys(enemigos).length + Object.keys(aliados).length > 0;

  function renderEquipo(equipo: Equipo, slots: Slots) {
    return (
      <ol className="slots">
        {POSICIONES.map((pos) => {
          const propio = equipo === "aliados" && pos === rol;
          return (
            <HeroSlot
              key={pos}
              equipo={equipo}
              posicion={pos}
              nombrePosicion={nombrePosicion(pos)}
              hero={propio ? (miPick ?? undefined) : slots[pos]}
              disponibles={disponibles}
              cargando={cargandoHeroes}
              propio={propio}
              onPick={(h) => elegir(equipo, pos, h)}
              onClear={() => (propio ? quitarPropio() : quitar(equipo, pos))}
              onCambiar={propio ? enfocarSugerencias : undefined}
            />
          );
        })}
      </ol>
    );
  }

  return (
    <div className="app">
      <header className="barra-superior">
        <h1 className="titulo">Consejero de draft</h1>

        <fieldset className="roles">
          <legend>Tu rol</legend>
          <div className="roles__grupo" role="radiogroup" aria-label="Tu rol">
            {roles.map((r) => (
              <label
                key={r.numero}
                className={r.numero === rol ? "rol rol--activo" : "rol"}
              >
                <input
                  type="radio"
                  name="rol"
                  value={r.numero}
                  checked={r.numero === rol}
                  onChange={() => cambiarRol(r.numero)}
                />
                <span className="rol__num">{r.numero}</span>
                <span className="rol__nombre">{nombreCorto(r.nombre)}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <div className="barra-superior__lado">
          <div className="campo">
            <label htmlFor="steam-id">Steam ID (opcional)</label>
            <input
              id="steam-id"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              placeholder="Para usar tu historial"
              value={steamId}
              aria-invalid={errorSteam ? true : undefined}
              aria-describedby={errorSteam ? "error-steam" : undefined}
              onChange={(e) => setSteamId(e.target.value)}
            />
            {errorSteam && (
              <p id="error-steam" className="campo__error" role="alert">
                {errorSteam}
              </p>
            )}
          </div>
          <button
            type="button"
            className="boton"
            onClick={reiniciar}
            disabled={!hayDraft}
          >
            Reiniciar draft
          </button>
        </div>
      </header>

      {errorHeroes && (
        <div className="aviso aviso--error" role="alert">
          <p>
            No se pudo cargar la lista de héroes. Revisa que la API esté
            corriendo en localhost:8080.
          </p>
          <button
            type="button"
            className="boton"
            onClick={() => setCargaHeroes((n) => n + 1)}
          >
            Reintentar
          </button>
        </div>
      )}

      <main className="tablero">
        <section
          className="equipo equipo--enemigos"
          aria-labelledby="titulo-dire"
        >
          <h2 id="titulo-dire" className="equipo__titulo">
            Dire
          </h2>
          <p className="equipo__sub">Enemigos</p>
          {renderEquipo("enemigos", enemigos)}
        </section>

        <Sugerencias
          estado={estado}
          heroes={heroes}
          onReintentar={reintentar}
          elegidoId={miPick?.id}
          onElegir={elegirPropio}
        />

        <section
          className="equipo equipo--aliados"
          aria-labelledby="titulo-radiant"
        >
          <h2 id="titulo-radiant" className="equipo__titulo">
            Radiant
          </h2>
          <p className="equipo__sub">Tu equipo</p>
          {renderEquipo("aliados", aliados)}
        </section>
      </main>

      <p className="sr-only" role="status" aria-live="polite">
        {anuncio}
      </p>
    </div>
  );
}
