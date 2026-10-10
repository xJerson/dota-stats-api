import { ApiError } from "./api";
import { describirError } from "./errors";
import { armarSolicitud, siguienteVacio } from "./draft";
import type { Hero } from "./types";

const hero = (id: number, nombre: string): Hero => ({ id, nombre, img: null, icono: null, roles: [], alias: [] });

describe("describirError", () => {
  it("muestra el mensaje del servidor para un perfil privado, junto al Steam ID", () => {
    const e = describirError(new ApiError("PERFIL_PRIVADO", "Activa tus datos públicos", 422));
    expect(e).toMatchObject({ zona: "steam", texto: "Activa tus datos públicos" });
  });

  it("trata DATOS_NO_CARGADOS como carga del meta", () => {
    expect(describirError(new ApiError("DATOS_NO_CARGADOS", "x", 503))).toMatchObject({
      zona: "meta",
      texto: "Cargando datos del meta…",
    });
  });

  it("traduce Steam ID inválido, OpenDota caído y la API apagada", () => {
    expect(describirError(new ApiError("STEAM_ID_INVALIDO", "x", 400)).zona).toBe("steam");
    expect(describirError(new ApiError("OPENDOTA_NO_DISPONIBLE", "x", 503)).texto).toMatch(/OpenDota/);
    expect(describirError(new ApiError("RED", "x")).texto).toBe(
      "No se pudo conectar con la API en localhost:8080. ¿Está corriendo?",
    );
  });
});

describe("armarSolicitud", () => {
  it("envía los nombres exactos, omite el slot propio y el steamId vacío", () => {
    const sol = armarSolicitud(
      2,
      { 1: hero(1, "Anti-Mage"), 3: hero(3, "Lion") },
      { 2: hero(2, "Pudge"), 4: hero(4, "Io") },
      "  ",
    );
    expect(sol).toEqual({ miRol: 2, enemigos: { "1": "Anti-Mage", "3": "Lion" }, aliados: { "4": "Io" } });
  });

  it("incluye el steamId recortado", () => {
    expect(armarSolicitud(1, {}, {}, " 123 ").steamId).toBe("123");
  });
});

describe("siguienteVacio", () => {
  it("avanza al siguiente slot vacío dando la vuelta", () => {
    expect(siguienteVacio({ 1: hero(1, "a"), 2: hero(2, "b") }, 2)).toBe(3);
    expect(siguienteVacio({ 1: hero(1, "a"), 3: hero(3, "c"), 4: hero(4, "d"), 5: hero(5, "e") }, 5)).toBe(2);
    expect(siguienteVacio({ 1: hero(1, "a") }, 1, 2)).toBe(3);
  });

  it("devuelve null si no queda ninguno", () => {
    const lleno = { 1: hero(1, "a"), 2: hero(2, "b"), 3: hero(3, "c"), 4: hero(4, "d"), 5: hero(5, "e") };
    expect(siguienteVacio(lleno, 3)).toBeNull();
  });
});
