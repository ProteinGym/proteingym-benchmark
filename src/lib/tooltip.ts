// Frosted hover tip that follows the pointer (same material as the site header).
// Usage: <g use:tooltip={"Title line\nmore detail"}>. The first line is bold.

let tip: HTMLDivElement | undefined;

function tipElement(): HTMLDivElement {
  if (!tip) {
    tip = document.createElement("div");
    tip.setAttribute("role", "tooltip");
    tip.className =
      "material pointer-events-none fixed z-50 max-w-xs rounded-lg border border-black/10 px-3 py-2 text-xs font-medium leading-snug text-gray-900 shadow-xl whitespace-pre-line first-line:font-semibold";
    tip.hidden = true;
    document.body.append(tip);
  }
  return tip;
}

export function tooltip(node: Element, text: string) {
  const OFFSET = 14;
  const move = (e: Event) => {
    const { clientX, clientY } = e as PointerEvent;
    const el = tipElement();
    el.textContent = text;
    el.hidden = false;
    // keep it on screen: flip to the other side of the pointer near the right/bottom edge
    const x = clientX + OFFSET + el.offsetWidth > innerWidth ? clientX - OFFSET - el.offsetWidth : clientX + OFFSET;
    const y = clientY + OFFSET + el.offsetHeight > innerHeight ? clientY - OFFSET - el.offsetHeight : clientY + OFFSET;
    el.style.left = `${Math.max(4, x)}px`;
    el.style.top = `${Math.max(4, y)}px`;
  };
  const hide = () => {
    if (tip) tip.hidden = true;
  };
  node.addEventListener("pointermove", move);
  node.addEventListener("pointerleave", hide);
  return {
    update(next: string) {
      text = next;
    },
    destroy() {
      node.removeEventListener("pointermove", move);
      node.removeEventListener("pointerleave", hide);
      hide();
    },
  };
}
