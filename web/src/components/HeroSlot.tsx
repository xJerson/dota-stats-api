import { useState } from "react";
import { idCambiar, idSlot } from "../draft";
import type { Equipo, Hero } from "../types";
import { HeroCombobox } from "./HeroCombobox";

interface Props {
  equipo: Equipo;
  posicion: number;
  nombrePosicion: string;
  hero: Hero | undefined;
  disponibles: Hero[];
  /** El slot del propio usuario: lo completan las sugerencias, no se elige. */
  propio?: boolean;
  onPick: (hero: Hero) => void;
  onClear: () => void;
}

export function HeroSlot({ equipo, posicion, nombrePosicion, hero, disponibles, propio, onPick, onClear }: Props) {
  const [editando, setEditando] = useState(false);
  const clase = ["slot", `slot--${equipo}`, hero ? "slot--lleno" : "", propio ? "slot--propio" : ""].join(" ").trim();
  const etiquetaEquipo = equipo === "enemigos" ? "enemigo" : "aliado";

  return (
    <li className={clase}>
      <span className="slot__pos" aria-hidden="true">
        {posicion}
      </span>
      <div className="slot__cuerpo">
        {propio ? (
          <div className="slot__propio">
            <strong>Tú</strong>
            <span>{nombrePosicion}. Aquí va uno de los picks sugeridos.</span>
          </div>
        ) : hero && !editando ? (
          <div key={hero.id} className="slot__retrato">
            <button
              type="button"
              className="slot__imagen"
              id={idCambiar(equipo, posicion)}
              aria-label={`Cambiar a ${hero.nombre}, posición ${posicion}`}
              onClick={() => setEditando(true)}
            >
              {hero.img ? <img src={hero.img} alt={hero.nombre} /> : <span className="slot__sin-imagen" />}
            </button>
            <span className="slot__nombre">{hero.nombre}</span>
            <span className="slot__acciones">
              <button type="button" className="slot__cambiar" onClick={() => setEditando(true)}>
                Cambiar
              </button>
              <button type="button" className="slot__quitar" aria-label={`Quitar a ${hero.nombre}`} onClick={onClear}>
                <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
                  <path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                </svg>
              </button>
            </span>
          </div>
        ) : (
          <div className="slot__vacio">
            <span className="slot__rol">{nombrePosicion}</span>
            <HeroCombobox
              id={idSlot(equipo, posicion)}
              heroes={disponibles}
              label={`Héroe ${etiquetaEquipo}, posición ${posicion}, ${nombrePosicion}`}
              autoFocus={editando}
              onPick={(h) => {
                setEditando(false);
                onPick(h);
              }}
              onCancel={editando ? () => setEditando(false) : undefined}
            />
          </div>
        )}
      </div>
    </li>
  );
}
