import { buscar, crearIndice, distancia, normalizar, resaltar } from "./search";
import type { Hero } from "./types";

const h = (id: number, nombre: string, alias: string[] = []): Hero => ({
  id,
  nombre,
  img: null,
  icono: null,
  roles: [],
  alias,
});

const heroes = [
  h(1, "Anti-Mage", ["am"]),
  h(2, "Shadow Fiend", ["sf"]),
  h(3, "Shadow Shaman"),
  h(4, "Bloodseeker", ["bs"]),
  h(5, "Slark"),
  h(6, "Invoker"),
  h(7, "Pudge"),
  h(8, "Spirit Breaker", ["sb"]),
  h(9, "Vengeful Spirit", ["vs"]),
  h(10, "Void Spirit"),
  h(11, "Nature's Prophet", ["np"]),
  h(12, "Timbersaw"),
];
const indice = crearIndice(heroes);
const nombres = (q: string) => buscar(indice, q).resultados.map((r) => r.hero.nombre);

describe("normalizar", () => {
  it("ignora mayúsculas, acentos, guiones y apóstrofes", () => {
    expect(normalizar("  Anti-MÁGE ")).toBe("anti mage");
    expect(normalizar("Nature's")).toBe("natures");
  });
});

describe("distancia", () => {
  it("cuenta una transposición como un error", () => {
    expect(distancia("slrak", "slark")).toBe(1);
    expect(distancia("pudje", "pudge")).toBe(1);
  });
});

describe("buscar", () => {
  it("pone primero el alias exacto", () => {
    expect(nombres("sf")[0]).toBe("Shadow Fiend");
    expect(nombres("SB")[0]).toBe("Spirit Breaker");
  });

  it("encuentra por prefijo y ordena el prefijo antes que el inicio de palabra", () => {
    expect(nombres("shad")).toEqual(["Shadow Fiend", "Shadow Shaman"]);
    expect(nombres("spirit")).toEqual(["Spirit Breaker", "Vengeful Spirit", "Void Spirit"]);
  });

  it("encuentra por inicio de palabra y por subcadena", () => {
    expect(nombres("blood")).toEqual(["Bloodseeker"]);
    expect(nombres("seeker")).toEqual(["Bloodseeker"]);
    expect(nombres("mber")).toEqual(["Timbersaw"]);
  });

  it("tolera espacios, guiones y apóstrofes", () => {
    expect(nombres("anti mag")[0]).toBe("Anti-Mage");
    expect(nombres("natures")[0]).toBe("Nature's Prophet");
  });

  it("corrige errores de tipeo y lo marca como aproximado", () => {
    for (const [escrito, esperado] of [
      ["slrak", "Slark"],
      ["invokr", "Invoker"],
      ["pudje", "Pudge"],
    ]) {
      const r = buscar(indice, escrito);
      expect(r.aproximado).toBe(true);
      expect(r.resultados[0].hero.nombre).toBe(esperado);
    }
  });

  it("no marca como aproximado un resultado directo", () => {
    expect(buscar(indice, "slark").aproximado).toBe(false);
  });

  it("no devuelve nada si no hay parecido", () => {
    expect(buscar(indice, "zzzzzz").resultados).toEqual([]);
    expect(buscar(indice, "").resultados).toEqual([]);
  });

  it("limita la cantidad de resultados", () => {
    expect(buscar(indice, "s", 3).resultados).toHaveLength(3);
  });
});

describe("resaltar", () => {
  it("marca el prefijo", () => {
    expect(resaltar("Bloodseeker", "blood")).toEqual([
      { texto: "Blood", resaltado: true },
      { texto: "seeker", resaltado: false },
    ]);
  });

  it("marca el inicio de una palabra intermedia", () => {
    expect(resaltar("Vengeful Spirit", "spirit")).toEqual([
      { texto: "Vengeful ", resaltado: false },
      { texto: "Spirit", resaltado: true },
    ]);
  });

  it("no marca nada si no hay coincidencia literal", () => {
    expect(resaltar("Slark", "slrak")).toEqual([{ texto: "Slark", resaltado: false }]);
  });
});
