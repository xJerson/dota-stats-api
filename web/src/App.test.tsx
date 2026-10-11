import { render, screen, waitFor, within } from "@testing-library/react";
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
  filas: [
    {
      id: "meta",
      titulo: "Sugerencias por meta",
      sugerencias: [
        { heroId: 3, heroe: "Pudge", puntaje: 0.55, motivo: "Fuerte contra Anti-Mage", confianza: null },
        { heroId: 2, heroe: "Lion", puntaje: 0.5, motivo: "Control de maná", confianza: null },
      ],
    },
  ],
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

  it("recuerda que el consejo es una ventaja para la fase de línea", () => {
    render(<App />);
    expect(
      screen.getByText("Es una ventaja para la fase de línea. El resto depende de cómo juegues."),
    ).toBeInTheDocument();
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

    await screen.findByRole("button", { name: /Pudge/ }, { timeout: 2000 });
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

  it("escribir y pulsar Enter antes de que llegue el catálogo elige el héroe cuando llega", async () => {
    let entregar: (r: Response) => void = () => undefined;
    const lento = new Promise<Response>((r) => (entregar = r));
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.endsWith("/roles")) return json(roles);
        if (url.endsWith("/heroes")) return lento;
        return json(sugerencias);
      }),
    );
    const user = userEvent.setup();
    render(<App />);
    const input = await screen.findByRole("combobox", { name: /enemigo, posición 1/ });

    await user.type(input, "am{Enter}");
    expect(input).toHaveValue("am");
    expect(screen.getAllByText(/Cargando héroes…/).length).toBeGreaterThan(0);
    expect(screen.queryByAltText("Anti-Mage")).not.toBeInTheDocument();

    entregar(new Response(JSON.stringify(heroes), { status: 200, headers: { "Content-Type": "application/json" } }));

    expect(await screen.findByAltText("Anti-Mage")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: /enemigo, posición 2/ })).toHaveFocus();
  });

  it("al llegar el catálogo no se pierde lo escrito en otro slot", async () => {
    let entregar: (r: Response) => void = () => undefined;
    const lento = new Promise<Response>((r) => (entregar = r));
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.endsWith("/roles")) return json(roles);
        if (url.endsWith("/heroes")) return lento;
        return json(sugerencias);
      }),
    );
    const user = userEvent.setup();
    render(<App />);
    const input = await screen.findByRole("combobox", { name: /enemigo, posición 3/ });
    await user.type(input, "pud");

    entregar(new Response(JSON.stringify(heroes), { status: 200, headers: { "Content-Type": "application/json" } }));

    expect(await screen.findByRole("option", { name: /Pudge/ })).toBeInTheDocument();
    expect(input).toHaveValue("pud");
    expect(input).toHaveFocus();
  });

  it("el nombre del slot Tú es el de la sugerencia aunque el catálogo use otra grafía", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.endsWith("/roles")) return json(roles);
        if (url.endsWith("/heroes")) return json(heroes.map((h) => (h.id === 3 ? { ...h, nombre: "Pudge (viejo)" } : h)));
        return json(sugerencias);
      }),
    );
    const user = userEvent.setup();
    render(<App />);
    const input = await screen.findByRole("combobox", { name: /enemigo, posición 1/ });
    await waitFor(async () => {
      await user.clear(input);
      await user.type(input, "am");
      expect(screen.getByRole("option", { name: /Anti-Mage/ })).toBeInTheDocument();
    });
    await user.keyboard("{Enter}");
    await user.click(await screen.findByRole("button", { name: /Pudge/ }, { timeout: 2000 }));

    const propio = screen.getByText("Tú", { selector: ".slot__tu" }).closest("li") as HTMLElement;
    expect(within(propio).getByText("Pudge")).toBeInTheDocument();
    expect(within(propio).queryByText(/viejo/)).not.toBeInTheDocument();
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

  describe("elegir una sugerencia", () => {
    async function conSugerencias() {
      const user = userEvent.setup();
      render(<App />);
      const input = await screen.findByRole("combobox", { name: /enemigo, posición 1/ });
      await waitFor(async () => {
        await user.clear(input);
        await user.type(input, "am");
        expect(screen.getByRole("option", { name: /Anti-Mage/ })).toBeInTheDocument();
      });
      await user.keyboard("{Enter}");
      const pudge = await screen.findByRole("button", { name: /Pudge/ }, { timeout: 2000 });
      return { user, pudge };
    }
    const slotPropio = () => screen.getByText("Tú").closest("li") as HTMLElement;
    const cuerpos = () =>
      (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls
        .filter((c) => c[0] === "/api/draft/suggest")
        .map((c) => JSON.parse(c[1].body));

    it("al hacer clic llena el slot Tú con retrato y nombre, y marca la sugerencia", async () => {
      const { user, pudge } = await conSugerencias();
      expect(pudge).toHaveAttribute("aria-pressed", "false");
      await user.click(pudge);

      expect(within(slotPropio()).getByAltText("Pudge")).toBeInTheDocument();
      expect(within(slotPropio()).getByText("Tú")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Pudge/, pressed: true })).toHaveTextContent("Elegido");
      expect(screen.getByRole("status")).toHaveTextContent("Elegiste Pudge para tu slot de Rol 2");
    });

    it("funciona con Enter y con Espacio", async () => {
      const { user, pudge } = await conSugerencias();
      pudge.focus();
      await user.keyboard("{Enter}");
      expect(within(slotPropio()).getByAltText("Pudge")).toBeInTheDocument();

      const lion = screen.getByRole("button", { name: /Lion/ });
      lion.focus();
      await user.keyboard(" ");
      expect(within(slotPropio()).getByAltText("Lion")).toBeInTheDocument();
      expect(within(slotPropio()).queryByAltText("Pudge")).not.toBeInTheDocument();
    });

    it("no envía el pick propio a /suggest ni reordena la lista", async () => {
      const { user, pudge } = await conSugerencias();
      const antes = cuerpos().length;
      await user.click(screen.getByRole("button", { name: /Lion/ }));
      await new Promise((r) => setTimeout(r, 500));
      expect(cuerpos().length).toBe(antes);
      for (const c of cuerpos()) expect(c.aliados).toEqual({});
      const botones = screen.getAllByRole("button", { name: /Pudge|Lion/ });
      expect(botones[0]).toBe(pudge);
      expect(botones[1]).toHaveTextContent("Elegido");
    });

    it("cambiar rol descarta el pick y reiniciar también", async () => {
      const { user } = await conSugerencias();
      await user.click(screen.getByRole("button", { name: /Pudge/ }));
      await user.click(screen.getByRole("radio", { name: /3/ }));
      expect(within(slotPropio()).queryByAltText("Pudge")).not.toBeInTheDocument();
      expect(slotPropio()).toHaveTextContent("Aquí va uno de los picks sugeridos");

      await user.click(screen.getByRole("button", { name: /Lion/ }));
      expect(within(slotPropio()).getByAltText("Lion")).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Reiniciar draft" }));
      expect(screen.getByText(/Aquí va uno de los picks sugeridos/)).toBeInTheDocument();
    });

    it("Quitar y Cambiar funcionan en el slot Tú", async () => {
      const { user } = await conSugerencias();
      await user.click(screen.getByRole("button", { name: /Pudge/ }));
      await user.click(within(slotPropio()).getByRole("button", { name: "Quitar a Pudge" }));
      expect(slotPropio()).toHaveTextContent("Aquí va uno de los picks sugeridos");
      expect(screen.getByRole("button", { name: /Pudge/ })).toHaveAttribute("aria-pressed", "false");

      await user.click(screen.getByRole("button", { name: /Pudge/ }));
      await user.click(within(slotPropio()).getByRole("button", { name: "Cambiar" }));
      expect(screen.getAllByRole("button", { name: /Pudge|Lion/ }).some((b) => b === document.activeElement)).toBe(true);
    });
  });
});
