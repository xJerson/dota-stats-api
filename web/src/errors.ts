import { ApiError } from "./api";

export type ZonaError = "steam" | "panel" | "meta";

export interface ErrorVisible {
  zona: ZonaError;
  texto: string;
  /** El usuario puede volver a intentar a mano. */
  reintentable: boolean;
}

export function describirError(e: unknown): ErrorVisible {
  const err = e instanceof ApiError ? e : new ApiError("DESCONOCIDO", "Algo salió mal");
  switch (err.codigo) {
    case "PERFIL_PRIVADO":
      return { zona: "steam", texto: err.mensaje, reintentable: false };
    case "STEAM_ID_INVALIDO":
      return {
        zona: "steam",
        texto: "Ese Steam ID no es válido. Usa tu SteamID64 o tu account ID numérico.",
        reintentable: false,
      };
    case "JUGADOR_NO_ENCONTRADO":
      return { zona: "steam", texto: "OpenDota no encontró a ese jugador.", reintentable: false };
    case "DATOS_NO_CARGADOS":
      return { zona: "meta", texto: "Cargando datos del meta…", reintentable: false };
    case "OPENDOTA_NO_DISPONIBLE":
      return {
        zona: "panel",
        texto: "OpenDota no responde en este momento. Intenta de nuevo en unos minutos.",
        reintentable: true,
      };
    case "RED":
      return {
        zona: "panel",
        texto: "No se pudo conectar con la API en localhost:8080. ¿Está corriendo?",
        reintentable: true,
      };
    default:
      return { zona: "panel", texto: err.mensaje || "Algo salió mal. Intenta de nuevo.", reintentable: true };
  }
}
