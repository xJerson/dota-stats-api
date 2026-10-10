import type { DraftRequest, Equipo, Hero, Slots } from "./types";

export const POSICIONES = [1, 2, 3, 4, 5] as const;

export function elegidos(enemigos: Slots, aliados: Slots): Set<number> {
  const ids = new Set<number>();
  for (const h of [...Object.values(enemigos), ...Object.values(aliados)]) {
    if (h) ids.add(h.id);
  }
  return ids;
}

function aMapa(slots: Slots, omitir?: number): Record<string, string> {
  const salida: Record<string, string> = {};
  for (const pos of POSICIONES) {
    const h = slots[pos];
    if (h && pos !== omitir) salida[String(pos)] = h.nombre;
  }
  return salida;
}

/** Arma el body de /suggest. Envía los nombres tal como los devolvió /heroes. */
export function armarSolicitud(rol: number, enemigos: Slots, aliados: Slots, steamId: string): DraftRequest {
  const solicitud: DraftRequest = {
    miRol: rol,
    enemigos: aMapa(enemigos),
    // el slot propio es el que el usuario está eligiendo: no se envía
    aliados: aMapa(aliados, rol),
  };
  if (steamId.trim()) solicitud.steamId = steamId.trim();
  return solicitud;
}

/** Siguiente slot vacío después de `desde`, dando la vuelta; null si no queda ninguno. */
export function siguienteVacio(slots: Slots, desde: number, omitir?: number): number | null {
  for (let i = 1; i < POSICIONES.length; i++) {
    const pos = ((desde - 1 + i) % POSICIONES.length) + 1;
    if (pos !== omitir && !slots[pos]) return pos;
  }
  return null;
}

export function ponerHeroe(slots: Slots, pos: number, hero: Hero | null): Slots {
  const copia = { ...slots };
  if (hero) copia[pos] = hero;
  else delete copia[pos];
  return copia;
}

export const idSlot = (equipo: Equipo, pos: number) => `slot-${equipo}-${pos}`;
export const ID_SLOT_PROPIO = "slot-propio";
export const idCambiar = (equipo: Equipo, pos: number) => `cambiar-${equipo}-${pos}`;

/** "Carry (hard carry)" pasa a "Carry" para etiquetas compactas. */
export function nombreCorto(nombre: string): string {
  return nombre.replace(/\s*\(.*\)\s*/g, "").trim();
}

/** Acerca el elemento lo mínimo (`block: "nearest"`), y solo si queda fuera de la ventana. */
export function acercarSiHaceFalta(el: HTMLElement | null): void {
  if (!el) return;
  const { top, bottom } = el.getBoundingClientRect();
  if ((top < 0 || bottom > window.innerHeight) && typeof el.scrollIntoView === "function") {
    el.scrollIntoView({ block: "nearest" });
  }
}

/** Pasa el foco sin que el navegador desplace la página por su cuenta; solo acerca el elemento si queda fuera. */
export function enfocarSinSaltar(el: HTMLElement | null): void {
  if (!el) return;
  el.focus({ preventScroll: true });
  acercarSiHaceFalta(el);
}
