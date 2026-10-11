export interface Hero {
  id: number;
  nombre: string;
  img: string | null;
  icono: string | null;
  roles: string[];
  alias: string[];
}

export interface Rol {
  numero: number;
  nombre: string;
}

export type Confianza = "alta" | "media" | "baja" | null;

export interface Sugerencia {
  heroId: number;
  heroe: string;
  puntaje: number;
  motivo: string;
  confianza: Confianza;
}

/** Una categoría de sugerencias (por meta, por jugabilidad, etc.), en el orden en que debe mostrarse. */
export interface Fila {
  id: string;
  titulo: string;
  sugerencias: Sugerencia[];
}

export interface DraftRequest {
  miRol: number;
  enemigos: Record<string, string>;
  aliados: Record<string, string>;
  steamId?: string;
}

export interface DraftResponse {
  miRol: number;
  filas: Fila[];
  advertencias: string[];
}

export interface EstadoMeta {
  cargado: boolean;
  cargando: boolean;
}

/** Slot (1..5) -> héroe elegido. */
export type Slots = Partial<Record<number, Hero>>;

export type Equipo = "enemigos" | "aliados";
