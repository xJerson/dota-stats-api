import { render, screen } from "@testing-library/react";
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
});
