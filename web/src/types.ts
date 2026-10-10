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

export interface Sugerencia {
  heroId: number;
  heroe: string;
  puntaje: number;
  motivo: string;
}

export interface DraftRequest {
  miRol: number;
  enemigos: Record<string, string>;
  aliados: Record<string, string>;
  steamId?: string;
}

export interface DraftResponse {
  miRol: number;
  sugerencias: Sugerencia[];
  advertencias: string[];
}

export interface EstadoMeta {
  cargado: boolean;
  cargando: boolean;
}

/** Slot (1..5) -> héroe elegido. */
export type Slots = Partial<Record<number, Hero>>;

export type Equipo = "enemigos" | "aliados";
