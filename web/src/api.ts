import type { DraftRequest, DraftResponse, EstadoMeta, Hero, Rol } from "./types";

export class ApiError extends Error {
  readonly codigo: string;
  readonly mensaje: string;
  readonly status: number;

  constructor(codigo: string, mensaje: string, status = 0) {
    super(mensaje);
    this.codigo = codigo;
    this.mensaje = mensaje;
    this.status = status;
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError("RED", "Sin conexión con la API");
  }
  if (!res.ok) {
    let codigo = "DESCONOCIDO";
    let mensaje = `La API respondió con el estado ${res.status}`;
    try {
      const body = (await res.json()) as { error?: string; mensaje?: string };
      if (body.error) codigo = body.error;
      if (body.mensaje) mensaje = body.mensaje;
    } catch {
      /* cuerpo sin JSON: se usan los valores por defecto */
    }
    throw new ApiError(codigo, mensaje, res.status);
  }
  return (await res.json()) as T;
}

export const obtenerRoles = (signal?: AbortSignal) => request<Rol[]>("/api/draft/roles", { signal });

export const obtenerHeroes = (signal?: AbortSignal) => request<Hero[]>("/api/draft/heroes", { signal });

export const obtenerEstado = (signal?: AbortSignal) => request<EstadoMeta>("/api/admin/estado", { signal });

export const pedirSugerencias = (body: DraftRequest, signal?: AbortSignal) =>
  request<DraftResponse>("/api/draft/suggest", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
  });
