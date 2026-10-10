import { useEffect, useId, useMemo, useState } from "react";
import type { KeyboardEvent } from "react";
import { buscar, crearIndice, resaltar } from "../search";
import type { Hero } from "../types";

interface Props {
  /** id del input (sirve para devolverle el foco desde fuera). */
  id: string;
  /** Héroes que todavía se pueden elegir. */
  heroes: Hero[];
  label: string;
  placeholder?: string;
  /** El catálogo de héroes todavía no llegó: el campo se puede usar y se filtra apenas llegue. */
  cargando?: boolean;
  autoFocus?: boolean;
  onPick: (hero: Hero) => void;
  /** Escape o salir del campo sin escribir nada. */
  onCancel?: () => void;
}

const MAX_SUGERENCIAS = 6;

export function HeroCombobox({
  id,
  heroes,
  label,
  placeholder = "Escribe un héroe",
  cargando = false,
  autoFocus,
  onPick,
  onCancel,
}: Props) {
  const listaId = useId();
  const avisoId = useId();
  const [consulta, setConsulta] = useState("");
  const [enfocado, setEnfocado] = useState(false);
  const [activo, setActivo] = useState(0);
  // Enter pulsado mientras el catálogo cargaba: se atiende en cuanto llega, en vez de perderse en silencio
  const [pendiente, setPendiente] = useState(false);

  const indice = useMemo(() => crearIndice(heroes), [heroes]);
  const { resultados, aproximado } = useMemo(
    () => buscar(indice, consulta, MAX_SUGERENCIAS),
    [indice, consulta],
  );

  useEffect(() => setActivo(0), [consulta]);

  useEffect(() => {
    if (cargando || !pendiente) return;
    setPendiente(false);
    if (resultados.length > 0) {
      setConsulta("");
      onPick(resultados[0].hero);
    }
    // onPick cambia en cada render del padre; solo importa el momento en que termina la carga
  }, [cargando, pendiente]);

  const hayConsulta = consulta.trim() !== "";
  const abierto = enfocado && hayConsulta;
  const idOpcion = (i: number) => `${listaId}-op-${i}`;

  function elegir(hero: Hero) {
    setConsulta("");
    onPick(hero);
  }

  function alPulsar(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Escape") {
      e.stopPropagation();
      setPendiente(false);
      if (hayConsulta) setConsulta("");
      else onCancel?.();
      return;
    }
    if (cargando) {
      if (e.key === "Enter" && hayConsulta) {
        e.preventDefault();
        setPendiente(true);
      }
      return;
    }
    if (!abierto || resultados.length === 0) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActivo((a) => (a + 1) % resultados.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActivo((a) => (a - 1 + resultados.length) % resultados.length);
    } else if (e.key === "Enter" || e.key === "Tab") {
      e.preventDefault();
      elegir(resultados[Math.min(activo, resultados.length - 1)].hero);
    }
  }

  return (
    <div className="combo">
      <input
        id={id}
        className="combo__input"
        type="text"
        role="combobox"
        aria-label={label}
        aria-expanded={abierto && resultados.length > 0}
        aria-controls={listaId}
        aria-autocomplete="list"
        aria-activedescendant={abierto && resultados.length > 0 ? idOpcion(activo) : undefined}
        aria-describedby={abierto ? avisoId : undefined}
        placeholder={cargando ? "Cargando héroes…" : placeholder}
        autoComplete="off"
        autoCapitalize="off"
        spellCheck={false}
        autoFocus={autoFocus}
        value={consulta}
        onChange={(e) => {
          setPendiente(false);
          setConsulta(e.target.value);
        }}
        onFocus={() => setEnfocado(true)}
        onBlur={() => {
          setEnfocado(false);
          setPendiente(false);
          if (!hayConsulta) onCancel?.();
        }}
        onKeyDown={alPulsar}
      />
      <div className="combo__panel" hidden={!abierto}>
        <p id={avisoId} className="combo__aviso" role="status">
          {abierto && cargando
            ? pendiente
              ? "Cargando héroes… Se elegirá la primera coincidencia al terminar."
              : "Cargando héroes…"
            : abierto && resultados.length === 0
            ? "No encontramos ese héroe. Prueba con otro nombre o una abreviatura como sf o am."
            : abierto && aproximado
              ? "¿Quisiste decir…?"
              : ""}
        </p>
        <ul id={listaId} className="combo__lista" role="listbox" aria-label={label} hidden={resultados.length === 0}>
          {resultados.map(({ hero }, i) => (
            <li
              key={hero.id}
              id={idOpcion(i)}
              role="option"
              aria-selected={i === activo}
              className={i === activo ? "combo__opcion combo__opcion--activa" : "combo__opcion"}
              // mouseDown (no click) para que el input no pierda el foco antes de elegir
              onMouseDown={(e) => {
                e.preventDefault();
                elegir(hero);
              }}
              onMouseMove={() => setActivo(i)}
            >
              {hero.icono ? <img src={hero.icono} alt="" loading="lazy" width={28} height={28} /> : <span className="combo__sin-icono" />}
              <span className="combo__nombre">
                {aproximado
                  ? hero.nombre
                  : resaltar(hero.nombre, consulta).map((t, k) =>
                      t.resaltado ? <mark key={k}>{t.texto}</mark> : <span key={k}>{t.texto}</span>,
                    )}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
