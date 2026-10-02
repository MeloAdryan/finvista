import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

const INTERACTIVE_SELECTOR = [
  "a",
  "button",
  "input",
  "select",
  "textarea",
  "label",
  "table",
  "[role='button']",
  "[role='link']",
  ".recharts-wrapper",
].join(",");

function useDragScroll<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);

  const [arrastando, setArrastando] =
    useState(false);

  const dragState = useRef({
    ativo: false,
    inicioX: 0,
    inicioY: 0,
    scrollX: 0,
    scrollY: 0,
  });

  const podeArrastar = useCallback(
    (target: EventTarget | null) => {
      if (!(target instanceof Element)) {
        return true;
      }

      return !target.closest(INTERACTIVE_SELECTOR);
    },
    [],
  );

  const iniciarArrasto = useCallback(
    (event: React.MouseEvent<T>) => {
      /*
       * Apenas botão esquerdo do mouse.
       */
      if (event.button !== 0) {
        return;
      }

      /*
       * Em controles interativos deixamos o comportamento
       * normal da interface.
       */
      if (!podeArrastar(event.target)) {
        return;
      }

      const elemento = ref.current;

      if (!elemento) {
        return;
      }

      dragState.current = {
        ativo: true,
        inicioX: event.clientX,
        inicioY: event.clientY,
        scrollX: elemento.scrollLeft,
        scrollY: elemento.scrollTop,
      };

      setArrastando(true);
    },
    [podeArrastar],
  );

  const moverArrasto = useCallback(
    (event: MouseEvent) => {
      const elemento = ref.current;
      const estado = dragState.current;

      if (!elemento || !estado.ativo) {
        return;
      }

      const deslocamentoX =
        event.clientX - estado.inicioX;

      const deslocamentoY =
        event.clientY - estado.inicioY;

      elemento.scrollLeft =
        estado.scrollX - deslocamentoX;

      elemento.scrollTop =
        estado.scrollY - deslocamentoY;
    },
    [],
  );

  const finalizarArrasto = useCallback(() => {
    if (!dragState.current.ativo) {
      return;
    }

    dragState.current.ativo = false;

    setArrastando(false);
  }, []);

  useEffect(() => {
    window.addEventListener(
      "mousemove",
      moverArrasto,
    );

    window.addEventListener(
      "mouseup",
      finalizarArrasto,
    );

    window.addEventListener(
      "blur",
      finalizarArrasto,
    );

    return () => {
      window.removeEventListener(
        "mousemove",
        moverArrasto,
      );

      window.removeEventListener(
        "mouseup",
        finalizarArrasto,
      );

      window.removeEventListener(
        "blur",
        finalizarArrasto,
      );
    };
  }, [moverArrasto, finalizarArrasto]);

  return {
    ref,
    arrastando,
    iniciarArrasto,
  };
}

export default useDragScroll;