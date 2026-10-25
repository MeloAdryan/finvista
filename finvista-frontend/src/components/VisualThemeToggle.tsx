import { useState } from "react";
import { applyVisualTheme, type VisualTheme } from "../services/visualTheme";
export default function VisualThemeToggle() {
  const [theme, setTheme] = useState<VisualTheme>(() =>
    document.documentElement.dataset.fvTheme === "light" ? "light" : "dark",
  );
  const next = theme === "dark" ? "light" : "dark";
  return (
    <button
      className="finvista-theme-toggle"
      type="button"
      aria-label={`Ativar tema ${next === "dark" ? "escuro" : "claro"}`}
      title={`Ativar tema ${next === "dark" ? "escuro" : "claro"}`}
      onClick={() => {
        applyVisualTheme(next);
        setTheme(next);
      }}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        {theme === "dark" ? (
          <>
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5" />
          </>
        ) : (
          <path d="M20 15.5A8.5 8.5 0 0 1 8.5 4a8.5 8.5 0 1 0 11.5 11.5Z" />
        )}
      </svg>
      <span>{theme === "dark" ? "Tema escuro" : "Tema claro"}</span>
    </button>
  );
}
