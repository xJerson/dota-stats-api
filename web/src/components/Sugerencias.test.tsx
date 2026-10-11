import { render, screen } from "@testing-library/react";
import { Sugerencias } from "./Sugerencias";
import type { EstadoSugerencias } from "../useSugerencias";
import type { Fila, Hero, Sugerencia } from "../types";

const hero = (id: number, nombre: string): Hero => ({
  id,
  nombre,
  img: null,
  icono: null,
  roles: [],
  alias: [],
});

const heroes = [hero(1, "Anti-Mage"), hero(2, "Lion"), hero(3, "Pudge"), hero(26, "Lion"), hero(76, "Outworld Devourer")];

function sugerencia(partial: Partial<Sugerencia>): Sugerencia {
  return {
    heroId: 1,
    heroe: "Anti-Mage",
    puntaje: 0.5,
    motivo: "motivo",
    confianza: null,
    ...partial,
  };
}

function fila(partial: Partial<Fila>): Fila {
  return { id: "x", titulo: "Fila", sugerencias: [], ...partial };
}

function estadoOk(filas: Fila[]): EstadoSugerencias {
  return { fase: "ok", datos: { miRol: 2, filas, advertencias: [] } };
}

function montar(estado: EstadoSugerencias) {
  render(
    <Sugerencias estado={estado} heroes={heroes} onReintentar={() => undefined} elegidoId={null} onElegir={() => undefined} />,
  );
}

describe("Sugerencias", () => {
  it("renderiza cada fila en el orden recibido, sin asumir una cantidad fija de filas", () => {
    const filas = [
      fila({ id: "meta", titulo: "Sugerencias por meta", sugerencias: [sugerencia({ heroId: 76, heroe: "Outworld Devourer" })] }),
      fila({ id: "habilidad", titulo: "Sugerencias por jugabilidad", sugerencias: [sugerencia({ heroId: 26, heroe: "Lion" })] }),
      fila({ id: "extra", titulo: "Otra categoría futura", sugerencias: [sugerencia({ heroId: 3, heroe: "Pudge" })] }),
    ];
    montar(estadoOk(filas));

    const titulos = screen.getAllByRole("heading", { level: 3 }).map((h) => h.textContent);
    expect(titulos).toEqual(["Sugerencias por meta", "Sugerencias por jugabilidad", "Otra categoría futura"]);
  });

  it("muestra un estado vacío específico para la fila de jugabilidad sin sugerencias", () => {
    const filas = [
      fila({ id: "meta", titulo: "Sugerencias por meta", sugerencias: [sugerencia({ heroId: 76, heroe: "Outworld Devourer" })] }),
      fila({ id: "habilidad", titulo: "Sugerencias por jugabilidad", sugerencias: [] }),
    ];
    montar(estadoOk(filas));

    expect(screen.getByText("elige al rival de tu línea para ver sugerencias por jugabilidad")).toBeInTheDocument();
  });

  it("usa un texto genérico cuando una fila desconocida no tiene sugerencias", () => {
    const filas = [fila({ id: "otra-desconocida", titulo: "Otra cosa", sugerencias: [] })];
    montar(estadoOk(filas));

    expect(screen.getByText("no hay sugerencias en esta categoría por ahora")).toBeInTheDocument();
  });

  it("muestra la confianza como una etiqueta de texto, no solo con color", () => {
    const filas = [
      fila({
        id: "habilidad",
        titulo: "Sugerencias por jugabilidad",
        sugerencias: [
          sugerencia({ heroId: 2, heroe: "Lion", confianza: "alta" }),
          sugerencia({ heroId: 3, heroe: "Pudge", confianza: "media" }),
        ],
      }),
    ];
    montar(estadoOk(filas));

    expect(screen.getByText(/confianza alta/i)).toBeInTheDocument();
    expect(screen.getByText(/confianza media/i)).toBeInTheDocument();
  });

  it("no muestra ninguna etiqueta de confianza cuando es null", () => {
    const filas = [
      fila({ id: "meta", titulo: "Sugerencias por meta", sugerencias: [sugerencia({ heroId: 76, heroe: "Outworld Devourer", confianza: null })] }),
    ];
    montar(estadoOk(filas));

    expect(screen.queryByText(/confianza/i)).not.toBeInTheDocument();
  });

  it("cada fila sigue permitiendo elegir una sugerencia", () => {
    const onElegir = vi.fn();
    const filas = [fila({ id: "meta", titulo: "Sugerencias por meta", sugerencias: [sugerencia({ heroId: 3, heroe: "Pudge" })] })];
    render(
      <Sugerencias estado={estadoOk(filas)} heroes={heroes} onReintentar={() => undefined} elegidoId={null} onElegir={onElegir} />,
    );
    screen.getByRole("button", { name: /Pudge/ }).click();
    expect(onElegir).toHaveBeenCalledWith(filas[0].sugerencias[0]);
  });

  it("4 filas se muestran igual que 2: no hay un número de filas hardcodeado", () => {
    const filas = Array.from({ length: 4 }, (_, i) =>
      fila({ id: `fila-${i}`, titulo: `Fila ${i}`, sugerencias: [sugerencia({ heroId: i + 1, heroe: `Heroe ${i}` })] }),
    );
    montar(estadoOk(filas));
    expect(screen.getAllByRole("heading", { level: 3 })).toHaveLength(4);
  });

  it("mantiene visible el aviso de ventaja de fase de línea", () => {
    montar(estadoOk([]));
    expect(screen.getByText("Es una ventaja para la fase de línea. El resto depende de cómo juegues.")).toBeInTheDocument();
  });

  it("sin filas en absoluto, muestra el vacío general del draft", () => {
    montar(estadoOk([]));
    expect(screen.getByText(/No hay sugerencias para este draft/)).toBeInTheDocument();
  });
});
