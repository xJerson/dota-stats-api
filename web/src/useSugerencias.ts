import { useCallback, useEffect, useState } from "react";
import { obtenerEstado, pedirSugerencias } from "./api";
import { armarSolicitud } from "./draft";
import { describirError } from "./errors";
import type { ErrorVisible } from "./errors";
import type { DraftRequest, DraftResponse, Slots } from "./types";

export type Fase = "inactivo" | "cargando" | "ok" | "meta" | "error";

export interface EstadoSugerencias {
  fase: Fase;
  datos?: DraftResponse;
  error?: ErrorVisible;
}

const ESPERA_MS = 300;
const SONDEO_MS = 3000;

export function useSugerencias(rol: number, enemigos: Slots, aliados: Slots, steamId: string) {
  const [estado, setEstado] = useState<EstadoSugerencias>({ fase: "inactivo" });
  const [intento, setIntento] = useState(0);
  const clave = JSON.stringify(armarSolicitud(rol, enemigos, aliados, steamId));

  useEffect(() => {
    const solicitud = JSON.parse(clave) as DraftRequest;
    if (Object.keys(solicitud.enemigos).length === 0) {
      setEstado({ fase: "inactivo" });
      return;
    }
    const control = new AbortController();
    let sondeo: ReturnType<typeof setTimeout> | undefined;
    setEstado((previo) => ({ fase: "cargando", datos: previo.datos }));

    const esperarMeta = async () => {
      try {
        const e = await obtenerEstado(control.signal);
        if (e.cargado) {
          setIntento((n) => n + 1);
          return;
        }
      } catch {
        if (control.signal.aborted) return;
      }
      sondeo = setTimeout(esperarMeta, SONDEO_MS);
    };

    const espera = setTimeout(async () => {
      try {
        const datos = await pedirSugerencias(solicitud, control.signal);
        setEstado({ fase: "ok", datos });
      } catch (e) {
        if (control.signal.aborted) return;
        const error = describirError(e);
        if (error.zona === "meta") {
          setEstado({ fase: "meta", error });
          sondeo = setTimeout(esperarMeta, SONDEO_MS);
        } else {
          setEstado({ fase: "error", error });
        }
      }
    }, ESPERA_MS);

    return () => {
      clearTimeout(espera);
      clearTimeout(sondeo);
      control.abort();
    };
  }, [clave, intento]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);
  return { estado, reintentar };
}
