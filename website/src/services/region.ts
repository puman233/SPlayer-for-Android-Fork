/** IP-country lookup only; never request device location or persist a visitor's IP. */
export const COUNTRY_URL = "https://api.country.is/";
export function parseCountry(payload: unknown): string | null {
  if (!payload || typeof payload !== "object" || !("country" in payload)) return null;
  const country = payload.country;
  return typeof country === "string" && /^[A-Z]{2}$/.test(country) ? country : null;
}
export async function detectCountry(request: typeof fetch = fetch): Promise<string | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 1800);
  try {
    const response = await request(COUNTRY_URL, {
      signal: controller.signal,
      credentials: "omit",
      referrerPolicy: "no-referrer",
      cache: "no-store",
    });
    return response.ok ? parseCountry(await response.json()) : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
