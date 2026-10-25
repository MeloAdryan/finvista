import { useId } from "react";
export const chartTones = [
  "blue",
  "orange",
  "success",
  "danger",
  "neutral",
  "purple",
] as const;
type Tone = (typeof chartTones)[number];
/** Paint only: does not alter series, domains, stacking or values. */
export function chartTone(color: string): Tone {
  const value = color.toLowerCase();
  if (value.includes("orange") || value.includes("warning")) return "orange";
  if (value.includes("success")) return "success";
  if (value.includes("danger")) return "danger";
  const raw = value.replace("#", "");
  if (!/^[0-9a-f]{3}([0-9a-f]{3})?$/.test(raw)) return "blue";
  const hex = raw.length === 3 ? [...raw].map((c) => c + c).join("") : raw;
  const [r, g, b] = [0, 2, 4].map(
      (i) => parseInt(hex.slice(i, i + 2), 16) / 255,
    ),
    max = Math.max(r, g, b),
    min = Math.min(r, g, b),
    delta = max - min;
  if (delta < 0.15) return "neutral";
  let h =
    delta === 0
      ? 0
      : max === r
        ? ((g - b) / delta) % 6
        : max === g
          ? (b - r) / delta + 2
          : (r - g) / delta + 4;
  h = (h * 60 + 360) % 360;
  return h < 18 || h >= 335
    ? "danger"
    : h < 75
      ? "orange"
      : h < 175
        ? "success"
        : h < 275
          ? "blue"
          : "purple";
}
export function useChartAppearance() {
  const id = "fv-paint-" + useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const paint = (color: string, area = false) =>
    color === "none" || color === "transparent" || color.startsWith("url(")
      ? color
      : `url(#${id}-${chartTone(color)}${area ? "-area" : ""})`;
  return {
    id,
    fill: (color: string) => paint(color),
    area: (color: string) => paint(color, true),
  };
}
