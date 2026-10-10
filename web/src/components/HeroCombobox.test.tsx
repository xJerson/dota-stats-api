import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HeroCombobox } from "./HeroCombobox";
import type { Hero } from "../types";

const h = (id: number, nombre: string, alias: string[] = []): Hero => ({
  id,
  nombre,
  img: `/img/${id}.png`,
  icono: `/ico/${id}.png`,
  roles: [],
  alias,
});

const heroes = [h(1, "Slark"), h(2, "Shadow Fiend", ["sf"]), h(3, "Shadow Shaman"), h(4, "Anti-Mage", ["am"])];

function montar() {
  const onPick = vi.fn();
  const onCancel = vi.fn();
  render(<HeroCombobox id="x" heroes={heroes} label="Héroe" onPick={onPick} onCancel={onCancel} />);
  return { onPick, onCancel, input: screen.getByRole("combobox", { name: "Héroe" }) };
}

describe("HeroCombobox", () => {
  it("encuentra por abreviatura y deja la primera opción resaltada", async () => {
    const user = userEvent.setup();
    const { input } = montar();
    await user.type(input, "sf");
    const opciones = screen.getAllByRole("option");
    expect(opciones[0]).toHaveTextContent("Shadow Fiend");
    expect(opciones[0]).toHaveAttribute("aria-selected", "true");
    expect(input).toHaveAttribute("aria-activedescendant", opciones[0].id);
  });

  it("Enter elige la primera opción", async () => {
    const user = userEvent.setup();
    const { input, onPick } = montar();
    await user.type(input, "shad{Enter}");
    expect(onPick).toHaveBeenCalledWith(heroes[1]);
    expect(input).toHaveValue("");
  });

  it("las flechas mueven la selección y Enter elige esa opción", async () => {
    const user = userEvent.setup();
    const { input, onPick } = montar();
    await user.type(input, "shad{ArrowDown}{Enter}");
    expect(onPick).toHaveBeenCalledWith(heroes[2]);
  });

  it("Tab acepta la sugerencia activa", async () => {
    const user = userEvent.setup();
    const { input, onPick } = montar();
    await user.type(input, "am{Tab}");
    expect(onPick).toHaveBeenCalledWith(heroes[3]);
  });

  it("el clic elige la opción", async () => {
    const user = userEvent.setup();
    const { input, onPick } = montar();
    await user.type(input, "shad");
    await user.click(screen.getByRole("option", { name: /Shadow Shaman/ }));
    expect(onPick).toHaveBeenCalledWith(heroes[2]);
  });

  it("corrige errores de tipeo con el encabezado de corrección", async () => {
    const user = userEvent.setup();
    const { input } = montar();
    await user.type(input, "slrak");
    expect(screen.getByText("¿Quisiste decir…?")).toBeVisible();
    expect(screen.getAllByRole("option")[0]).toHaveTextContent("Slark");
  });

  it("avisa cuando no hay nada parecido", async () => {
    const user = userEvent.setup();
    const { input, onPick } = montar();
    await user.type(input, "qqqqqqq{Enter}");
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
    expect(screen.getByText(/No encontramos ese héroe/)).toBeVisible();
    expect(onPick).not.toHaveBeenCalled();
  });

  it("Escape limpia lo escrito y, con el campo vacío, cancela", async () => {
    const user = userEvent.setup();
    const { input, onCancel } = montar();
    await user.type(input, "sh{Escape}");
    expect(input).toHaveValue("");
    expect(onCancel).not.toHaveBeenCalled();
    await user.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalled();
  });

  it("resalta la parte que coincide sin cambiar de color", async () => {
    const user = userEvent.setup();
    const { input } = montar();
    await user.type(input, "shad");
    const marcas = screen.getAllByText("Shad");
    expect(marcas[0].tagName).toBe("MARK");
  });

  describe("mientras carga el catálogo de héroes", () => {
    function montarCargando() {
      const onPick = vi.fn();
      const vista = render(<HeroCombobox id="x" heroes={[]} cargando label="Héroe" onPick={onPick} />);
      const rerender = (props: { heroes: Hero[]; cargando: boolean }) =>
        vista.rerender(<HeroCombobox id="x" label="Héroe" onPick={onPick} {...props} />);
      return { onPick, rerender, input: screen.getByRole("combobox", { name: "Héroe" }) };
    }

    it("avisa que está cargando en vez de decir que no encontró el héroe, y el campo se puede usar", async () => {
      const user = userEvent.setup();
      const { input } = montarCargando();
      expect(input).toHaveAttribute("placeholder", "Cargando héroes…");
      await user.type(input, "sf");

      expect(input).toHaveValue("sf");
      expect(screen.getByRole("status")).toHaveTextContent("Cargando héroes…");
      expect(screen.queryByText(/No encontramos ese héroe/)).not.toBeInTheDocument();
    });

    it("conserva lo escrito y lo filtra en cuanto llega la lista", async () => {
      const user = userEvent.setup();
      const { input, rerender } = montarCargando();
      await user.type(input, "sf");

      rerender({ heroes, cargando: false });

      expect(input).toHaveValue("sf");
      expect(screen.getByRole("option", { name: /Shadow Fiend/ })).toBeInTheDocument();
    });

    it("Enter antes de que llegue la lista no se pierde: elige la primera coincidencia al llegar", async () => {
      const user = userEvent.setup();
      const { input, onPick, rerender } = montarCargando();
      await user.type(input, "sf{Enter}");
      expect(onPick).not.toHaveBeenCalled();
      expect(input).toHaveValue("sf");
      expect(screen.getByRole("status")).toHaveTextContent("Cargando héroes…");

      rerender({ heroes, cargando: false });

      expect(onPick).toHaveBeenCalledTimes(1);
      expect(onPick).toHaveBeenCalledWith(heroes[1]);
      expect(input).toHaveValue("");
    });

    it("el Enter pendiente se cancela si se sigue escribiendo, se pulsa Escape o se sale del campo", async () => {
      const user = userEvent.setup();
      for (const cancelar of ["{Escape}", "x", "blur"]) {
        const { input, onPick, rerender } = montarCargando();
        await user.type(input, "sf{Enter}");
        if (cancelar === "blur") await user.tab();
        else await user.type(input, cancelar);
        rerender({ heroes, cargando: false });
        expect(onPick).not.toHaveBeenCalled();
        document.body.innerHTML = "";
      }
    });

    it("si al llegar la lista no hay coincidencia, lo dice y no elige nada", async () => {
      const user = userEvent.setup();
      const { input, onPick, rerender } = montarCargando();
      await user.type(input, "zzzzqq{Enter}");

      await act(async () => rerender({ heroes, cargando: false }));

      expect(onPick).not.toHaveBeenCalled();
      expect(screen.getByText(/No encontramos ese héroe/)).toBeInTheDocument();
    });
  });
});
