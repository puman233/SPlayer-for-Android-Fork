export function mountTheme(): void {
  const select = document.getElementById("theme-select");
  if (!(select instanceof HTMLSelectElement)) return;
  const system = window.matchMedia("(prefers-color-scheme: dark)");
  let preference = "system";
  try {
    preference = localStorage.getItem("splayer-theme") ?? "system";
  } catch {
    /* optional */
  }
  if (!["system", "light", "dark"].includes(preference)) preference = "system";
  const apply = (): void => {
    document.documentElement.dataset["theme"] =
      preference === "system" ? (system.matches ? "dark" : "light") : preference;
    select.value = preference;
  };
  select.addEventListener("change", () => {
    preference = select.value;
    try {
      localStorage.setItem("splayer-theme", preference);
    } catch {
      /* optional */
    }
    apply();
  });
  system.addEventListener("change", apply);
  apply();
}
