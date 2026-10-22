import { useEffect } from "react";

export function useMouseDragScroll(container: HTMLElement | null) {
  useEffect(() => {
    const nav = container?.querySelector<HTMLElement>(".finvista-navigation");
    if (!nav) return;
    let gesture: {
      id: number;
      y: number;
      scroll: number;
      dragging: boolean;
    } | null = null;
    let blockClick = false;
    let timer: number | undefined;
    const finish = () => {
      gesture = null;
      nav.classList.remove("finvista-navigation-dragging");
      window.clearTimeout(timer);

      timer = window.setTimeout(() => {
        blockClick = false;
      }, 0);
    };
    const down = (e: PointerEvent) => {
      if (
        e.pointerType !== "mouse" ||
        !e.isPrimary ||
        e.button !== 0 ||
        e.ctrlKey ||
        e.metaKey ||
        e.altKey ||
        e.shiftKey
      )
        return;
      if (nav.scrollHeight <= nav.clientHeight) return;
      window.clearTimeout(timer);
      blockClick = false;
      gesture = {
        id: e.pointerId,
        y: e.clientY,
        scroll: nav.scrollTop,
        dragging: false,
      };
    };
    const move = (e: PointerEvent) => {
      if (!gesture || e.pointerId !== gesture.id) return;
      if (!(e.buttons & 1)) {
        finish();
        return;
      }
      const distance = e.clientY - gesture.y;
      if (!gesture.dragging && Math.abs(distance) < 6) return;
      gesture.dragging = true;
      blockClick = true;
      nav.classList.add("finvista-navigation-dragging");
      e.preventDefault();
      nav.scrollTop = gesture.scroll - distance;
    };
    const up = (e: PointerEvent) => {
      if (gesture?.id === e.pointerId) finish();
    };
    const click = (e: MouseEvent) => {
      if (!blockClick || e.detail === 0) return; // Enter e tecnologias assistivas não são bloqueados.
      e.preventDefault();
      e.stopImmediatePropagation();
      blockClick = false;
    };
    const dragstart = (e: DragEvent) => e.preventDefault();
    const hidden = () => {
      if (document.hidden) finish();
    };
    nav.addEventListener("pointerdown", down);
    nav.addEventListener("click", click, true);
    nav.addEventListener("dragstart", dragstart);
    document.addEventListener("pointermove", move, { passive: false });
    document.addEventListener("pointerup", up);
    document.addEventListener("pointercancel", up);
    document.addEventListener("visibilitychange", hidden);
    window.addEventListener("blur", finish);
    return () => {
      nav.removeEventListener("pointerdown", down);
      nav.removeEventListener("click", click, true);
      nav.removeEventListener("dragstart", dragstart);
      document.removeEventListener("pointermove", move);
      document.removeEventListener("pointerup", up);
      document.removeEventListener("pointercancel", up);
      document.removeEventListener("visibilitychange", hidden);
      window.removeEventListener("blur", finish);
      window.clearTimeout(timer);
      nav.classList.remove("finvista-navigation-dragging");
    };
  }, [container]);
}
