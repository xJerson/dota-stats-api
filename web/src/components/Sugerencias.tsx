import type { EstadoSugerencias } from "../useSugerencias";
import type { Hero, Sugerencia } from "../types";

interface Props {
  estado: EstadoSugerencias;
  heroes: Hero[];
  onReintentar: () => void;
  /** Héroe elegido para el slot propio; solo se marca, la lista no cambia. */
  elegidoId?: number | null;
  onElegir: (sugerencia: Sugerencia) => void;
}

const formato = new Intl.NumberFormat("es", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Ancho de la barra: relativo a las otras sugerencias, nunca vacío. */
export function anchoBarra(puntaje: number, todos: number[]): number {
  const min = Math.min(...todos);
  const max = Math.max(...todos);
  if (max === min) return 100;
  return Math.round(35 + (65 * (puntaje - min)) / (max - min));
}

export function Sugerencias({ estado, heroes, onReintentar, elegidoId, onElegir }: Props) {
  const { fase, datos, error } = estado;
  const sugerencias = datos?.sugerencias ?? [];
  const puntajes = sugerencias.map((s) => s.puntaje);
  const firma = sugerencias.map((s) => s.heroId).join("-");

  return (
    <section className="centro" aria-labelledby="titulo-sugerencias" aria-busy={fase === "cargando"}>
      <h2 id="titulo-sugerencias" className="centro__titulo">
        Mejores picks para ti
      </h2>
      <p className="centro__nota">Es una ventaja para la fase de línea. El resto depende de cómo juegues.</p>

      {fase === "inactivo" && (
        <p className="vacio">Elige tu rol y agrega los héroes enemigos a medida que aparezcan.</p>
      )}

      {fase === "meta" && (
        <p className="aviso" role="status">
          {error?.texto}
        </p>
      )}

      {fase === "error" && error?.zona === "panel" && (
        <div className="aviso aviso--error" role="alert">
          <p>{error.texto}</p>
          {error.reintentable && (
            <button type="button" className="boton" onClick={onReintentar}>
              Reintentar
            </button>
          )}
        </div>
      )}

      {fase === "error" && error?.zona === "steam" && (
        <p className="aviso aviso--error">No pudimos usar tu historial. Revisa el Steam ID o quítalo para ver sugerencias del meta.</p>
      )}

      {sugerencias.length > 0 && fase !== "error" && fase !== "meta" && (
        <>
          <ol className="sugerencias" key={firma} data-cargando={fase === "cargando"}>
            {sugerencias.map((s, i) => {
              const hero = heroes.find((h) => h.id === s.heroId);
              const elegido = s.heroId === elegidoId;
              return (
                <li key={s.heroId} className="sugerencia" style={{ ["--i" as string]: i }}>
                  <button
                    type="button"
                    className={elegido ? "sugerencia__boton sugerencia__boton--elegida" : "sugerencia__boton"}
                    aria-pressed={elegido}
                    onClick={() => onElegir(s)}
                  >
                    <span className="sugerencia__rango" aria-label={`Opción ${i + 1}`}>
                      {i + 1}
                    </span>
                    {hero?.img ? (
                      <img className="sugerencia__img" src={hero.img} alt="" />
                    ) : (
                      <span className="sugerencia__img sugerencia__img--vacia" />
                    )}
                    <span className="sugerencia__texto">
                      <span className="sugerencia__cabecera">
                        <span className="sugerencia__nombre">{s.heroe}</span>
                        {elegido && <span className="sugerencia__elegido">Elegido</span>}
                        <span className="sugerencia__puntaje">Puntaje {formato.format(s.puntaje * 100)}</span>
                      </span>
                      <span className="barra" aria-hidden="true">
                        <span className="barra__relleno" style={{ width: `${anchoBarra(s.puntaje, puntajes)}%` }} />
                      </span>
                      <span className="sugerencia__motivo">{s.motivo}</span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
          {datos && datos.advertencias.length > 0 && (
            <ul className="advertencias">
              {datos.advertencias.map((a) => (
                <li key={a}>{a}</li>
              ))}
            </ul>
          )}
        </>
      )}

      {fase === "ok" && sugerencias.length === 0 && (
        <p className="vacio">No hay sugerencias para este draft. Prueba con otro rol o quita algún héroe.</p>
      )}
    </section>
  );
}
