import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";

const heroes = [
  { id: 1, nombre: "Anti-Mage", img: "/a.png", icono: "/ai.png", roles: [], alias: ["am"] },
  { id: 2, nombre: "Lion", img: "/l.png", icono: "/li.png", roles: [], alias: [] },
  { id: 3, nombre: "Pudge", img: "/p.png", icono: "/pi.png", roles: [], alias: [] },
];
const roles = [1, 2, 3, 4, 5].map((n) => ({ numero: n, nombre: `Rol ${n}` }));
const sugerencias = {
  miRol: 2,
  sugerencias: [{ heroId: 3, heroe: "Pudge", puntaje: 0.55, motivo: "Fuerte contra Lion" }],
  advertencias: [],
};

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

describe("App", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.endsWith("/roles")) return json(roles);
        if (url.endsWith("/heroes")) return json(heroes);
        return json(sugerencias);
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it("muestra el estado vacío y no llama a /suggest hasta que haya un enemigo", async () => {
    render(<App />);
    expect(screen.getByText(/Elige tu rol y agrega los héroes enemigos/)).toBeInTheDocument();
    const llamadas = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.map((c) => c[0]);
    expect(llamadas).not.toContain("/api/draft/suggest");
  });

  it("elegir un enemigo pide sugerencias con el nombre exacto, mueve el foco y permite reiniciar", async () => {
    const user = userEvent.setup();
    render(<App />);
    const input = await screen.findByRole("combobox", { name: /enemigo, posición 1/ });
    // espera a que cargue la lista de héroes
    await waitFor(async () => {
      await user.clear(input);
      await user.type(input, "am");
      expect(screen.getByRole("option", { name: /Anti-Mage/ })).toBeInTheDocument();
    });
    await user.keyboard("{Enter}");

    expect(screen.getByAltText("Anti-Mage")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: /enemigo, posición 2/ })).toHaveFocus();

    await screen.findByRole("heading", { name: "Pudge" }, { timeout: 2000 });
    const suggest = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.find((c) => c[0] === "/api/draft/suggest");
    expect(JSON.parse(suggest![1].body)).toEqual({ miRol: 2, enemigos: { "1": "Anti-Mage" }, aliados: {} });

    await user.click(screen.getByRole("button", { name: "Reiniciar draft" }));
    expect(screen.queryByAltText("Anti-Mage")).not.toBeInTheDocument();
    expect(screen.getByText(/Elige tu rol y agrega/)).toBeInTheDocument();
  });

  it("al elegir un héroe mueve el foco al siguiente slot sin desplazar la página", async () => {
    const user = userEvent.setup();
    const enfoque = vi.spyOn(HTMLElement.prototype, "focus");
    render(<App />);
    const input = await screen.findByRole("combobox", { name: /enemigo, posición 1/ });
    await waitFor(async () => {
      await user.clear(input);
      await user.type(input, "am");
      expect(screen.getByRole("option", { name: /Anti-Mage/ })).toBeInTheDocument();
    });
    enfoque.mockClear();
    await user.keyboard("{Enter}");

    const siguiente = screen.getByRole("combobox", { name: /enemigo, posición 2/ });
    expect(siguiente).toHaveFocus();
    expect(enfoque.mock.contexts).toContain(siguiente);
    const i = enfoque.mock.contexts.indexOf(siguiente);
    expect(enfoque.mock.calls[i][0]).toEqual({ preventScroll: true });
    enfoque.mockRestore();
  });

  it("guarda el Steam ID y marca el slot propio como Tú", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.type(screen.getByLabelText("Steam ID (opcional)"), "123");
    expect(localStorage.getItem("dota-draft.steamId")).toBe("123");
    expect(screen.getByText("Tú")).toBeInTheDocument();
  });

  it("explica cuando la API no responde", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("fail"))));
    render(<App />);
    expect(await screen.findByText(/No se pudo cargar la lista de héroes/)).toBeInTheDocument();
  });
});
