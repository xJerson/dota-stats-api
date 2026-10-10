import type { Hero } from "./types";

/** Minúsculas, sin acentos, apóstrofes eliminados y guiones/espacios como separador único. */
export function normalizar(texto: string): string {
  return texto
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/['’`]/g, "")
    .replace(/[-_]+/g, " ")
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

const compacto = (t: string) => t.replace(/ /g, "");

/** Distancia Damerau-Levenshtein (transposición adyacente cuenta como 1). */
export function distancia(a: string, b: string): number {
  const filas = a.length + 1;
  const cols = b.length + 1;
  const d: number[][] = Array.from({ length: filas }, (_, i) => {
    const fila = new Array<number>(cols).fill(0);
    fila[0] = i;
    return fila;
  });
  for (let j = 0; j < cols; j++) d[0][j] = j;
  for (let i = 1; i < filas; i++) {
    for (let j = 1; j < cols; j++) {
      const costo = a[i - 1] === b[j - 1] ? 0 : 1;
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + costo);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
      }
    }
  }
  return d[filas - 1][cols - 1];
}

/** Errores tolerados según el largo de lo escrito. */
export function umbral(largo: number): number {
  if (largo < 4) return 0;
  return largo < 6 ? 1 : 2;
}

interface Entrada {
  hero: Hero;
  nombre: string; // normalizado, con espacios
  junto: string; // normalizado, sin espacios
  palabras: string[];
  alias: string[];
}

export type Indice = Entrada[];

export function crearIndice(heroes: Hero[]): Indice {
  return heroes.map((hero) => {
    const nombre = normalizar(hero.nombre);
    return {
      hero,
      nombre,
      junto: compacto(nombre),
      palabras: nombre.split(" "),
      alias: hero.alias.map((a) => compacto(normalizar(a))),
    };
  });
}

export interface Resultado {
  hero: Hero;
  /** 0 alias/nombre exacto, 1 prefijo, 2 inicio de palabra, 3 subcadena, 4 aproximado */
  nivel: number;
}

export interface Busqueda {
  resultados: Resultado[];
  /** No hubo coincidencia directa: todo lo mostrado es una corrección aproximada. */
  aproximado: boolean;
}

function nivelDirecto(e: Entrada, q: string): number | null {
  if (e.junto === q || e.alias.includes(q)) return 0;
  if (e.junto.startsWith(q)) return 1;
  if (e.palabras.some((p) => p.startsWith(q)) || e.alias.some((a) => a.startsWith(q))) return 2;
  if (e.junto.includes(q)) return 3;
  return null;
}

function distanciaAproximada(e: Entrada, q: string): number {
  const candidatos = [
    distancia(q, e.junto),
    // lo escrito puede ser un prefijo con errores ("invokr" frente a "invoker")
    distancia(q, e.junto.slice(0, q.length)),
    distancia(q, e.junto.slice(0, q.length + 1)),
    ...e.palabras.map((p) => distancia(q, p)),
    ...e.alias.map((a) => distancia(q, a)),
  ];
  return Math.min(...candidatos);
}

export function buscar(indice: Indice, consulta: string, limite = 6): Busqueda {
  const q = compacto(normalizar(consulta));
  if (!q) return { resultados: [], aproximado: false };

  const directos: Resultado[] = [];
  for (const e of indice) {
    const nivel = nivelDirecto(e, q);
    if (nivel !== null) directos.push({ hero: e.hero, nivel });
  }
  if (directos.length > 0) {
    directos.sort((a, b) => a.nivel - b.nivel || a.hero.nombre.localeCompare(b.hero.nombre, "es"));
    return { resultados: directos.slice(0, limite), aproximado: false };
  }

  const max = umbral(q.length);
  if (max === 0) return { resultados: [], aproximado: false };
  const cercanos: { hero: Hero; d: number }[] = [];
  for (const e of indice) {
    const d = distanciaAproximada(e, q);
    if (d <= max) cercanos.push({ hero: e.hero, d });
  }
  cercanos.sort((a, b) => a.d - b.d || a.hero.nombre.localeCompare(b.hero.nombre, "es"));
  return {
    resultados: cercanos.slice(0, limite).map((c) => ({ hero: c.hero, nivel: 4 })),
    aproximado: cercanos.length > 0,
  };
}

export interface Tramo {
  texto: string;
  resaltado: boolean;
}

/** Parte `nombre` en tramos marcando lo que coincide con `consulta` (prefijo, inicio de palabra o subcadena). */
export function resaltar(nombre: string, consulta: string): Tramo[] {
  const q = compacto(normalizar(consulta));
  if (!q) return [{ texto: nombre, resaltado: false }];

  // letras/dígitos del nombre original, con su posición
  const letras: { c: string; pos: number }[] = [];
  [...nombre].forEach((ch, pos) => {
    const base = ch.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
    for (const c of base.replace(/[^a-z0-9]/g, "")) letras.push({ c, pos });
  });
  const junto = letras.map((l) => l.c).join("");
  const iniciosPalabra = new Set<number>();
  letras.forEach((l, i) => {
    if (i === 0 || !/[a-z0-9]/i.test(nombre[l.pos - 1] ?? " ")) iniciosPalabra.add(i);
  });

  let inicio = -1;
  for (let i = junto.indexOf(q); i !== -1; i = junto.indexOf(q, i + 1)) {
    if (iniciosPalabra.has(i)) {
      inicio = i;
      break;
    }
    if (inicio === -1) inicio = i;
  }
  if (inicio === -1) return [{ texto: nombre, resaltado: false }];

  const desde = letras[inicio].pos;
  const hasta = letras[inicio + q.length - 1].pos + 1;
  const tramos: Tramo[] = [];
  if (desde > 0) tramos.push({ texto: nombre.slice(0, desde), resaltado: false });
  tramos.push({ texto: nombre.slice(desde, hasta), resaltado: true });
  if (hasta < nombre.length) tramos.push({ texto: nombre.slice(hasta), resaltado: false });
  return tramos;
}
