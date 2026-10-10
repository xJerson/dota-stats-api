import { enfocarSinSaltar } from "./draft";

function elemento(top: number, bottom: number) {
  const el = document.createElement("input");
  document.body.appendChild(el);
  el.focus = vi.fn();
  el.scrollIntoView = vi.fn();
  el.getBoundingClientRect = () => ({ top, bottom }) as DOMRect;
  return el;
}

describe("enfocarSinSaltar", () => {
  beforeEach(() => {
    vi.stubGlobal("innerHeight", 600);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = "";
  });

  it("enfoca sin dejar que el navegador desplace la página", () => {
    const el = elemento(100, 140);
    enfocarSinSaltar(el);
    expect(el.focus).toHaveBeenCalledWith({ preventScroll: true });
  });

  it("no desplaza si el elemento ya está a la vista", () => {
    const el = elemento(100, 140);
    enfocarSinSaltar(el);
    expect(el.scrollIntoView).not.toHaveBeenCalled();
  });

  it("desplaza lo mínimo si queda por debajo del borde visible", () => {
    const el = elemento(580, 640);
    enfocarSinSaltar(el);
    expect(el.scrollIntoView).toHaveBeenCalledWith({ block: "nearest" });
  });

  it("desplaza lo mínimo si queda por encima del borde visible", () => {
    const el = elemento(-30, 10);
    enfocarSinSaltar(el);
    expect(el.scrollIntoView).toHaveBeenCalledWith({ block: "nearest" });
  });

  it("tolera un elemento nulo", () => {
    expect(() => enfocarSinSaltar(null)).not.toThrow();
  });
});
