import { useState } from "react";
import { ID_SLOT_PROPIO, idCambiar, idSlot } from "../draft";
import type { Equipo, Hero } from "../types";
import { HeroCombobox } from "./HeroCombobox";

interface Props {
  equipo: Equipo;
  posicion: number;
  nombrePosicion: string;
  hero: Hero | undefined;
  disponibles: Hero[];
  /** El catálogo de héroes todavía se está cargando. */
  cargando?: boolean;
  /** El slot del propio usuario: lo completan las sugerencias, no se elige. */
  propio?: boolean;
  onPick: (hero: Hero) => void;
  onClear: () => void;
  /** Solo en el slot propio: vuelve a las sugerencias para elegir otro. */
  onCambiar?: () => void;
}

export function HeroSlot({ equipo, posicion, nombrePosicion, hero, disponibles, cargando, propio, onPick, onClear, onCambiar }: Props) {
  const [editando, setEditando] = useState(false);
  const clase = ["slot", `slot--${equipo}`, hero ? "slot--lleno" : "", propio ? "slot--propio" : ""].join(" ").trim();
  const etiquetaEquipo = equipo === "enemigos" ? "enemigo" : "aliado";

  return (
    <li className={clase} id={propio ? ID_SLOT_PROPIO : undefined}>
      <span className="slot__pos" aria-hidden="true">
        {posicion}
      </span>
      <div className="slot__cuerpo">
        {propio && !hero ? (
          <div className="slot__propio">
            <strong>Tú</strong>
            <span>{nombrePosicion}. Aquí va uno de los picks sugeridos.</span>
          </div>
        ) : hero && (propio || !editando) ? (
          <div key={hero.id} className="slot__retrato">
            <button
              type="button"
              className="slot__imagen"
              id={idCambiar(equipo, posicion)}
              aria-label={`Cambiar a ${hero.nombre}, posición ${posicion}`}
              onClick={() => (propio ? onCambiar?.() : setEditando(true))}
            >
              {hero.img ? <img src={hero.img} alt={hero.nombre} /> : <span className="slot__sin-imagen" />}
            </button>
            {propio && (
              <span className="slot__tu">Tú</span>
            )}
            <span className="slot__nombre">{hero.nombre}</span>
            <span className="slot__acciones">
              <button
                type="button"
                className="slot__cambiar"
                onClick={() => (propio ? onCambiar?.() : setEditando(true))}
              >
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
              cargando={cargando}
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
