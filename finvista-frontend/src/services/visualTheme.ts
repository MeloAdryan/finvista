export type VisualTheme = "dark" | "light";
export const THEME_KEY = "finvista-visual-theme-v1";
export function initializeVisualTheme(): VisualTheme {
 let theme:VisualTheme="dark";
 try { if(localStorage.getItem(THEME_KEY)==="light")theme="light"; } catch { /* A preferência continua disponível nesta sessão. */ }
 document.documentElement.dataset.fvTheme=theme;
 return theme;
}
export function applyVisualTheme(theme:VisualTheme){
 document.documentElement.dataset.fvTheme=theme;
 try {localStorage.setItem(THEME_KEY,theme);} catch { 
    /* Armazenamento bloqueado não impede a alternância. */ }
}
