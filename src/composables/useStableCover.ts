import { computed, onScopeDispose, shallowRef, watch } from "vue";

export interface CoverFrame {
  key: number;
  source: string;
}

/** Keep the rendered frame until a replacement's actual image has decoded. */
export function useStableCover(options: {
  sources: () => readonly (string | undefined)[];
  active: () => boolean;
  intervalMs?: number;
}) {
  const current = shallowRef<CoverFrame>();
  const pending = shallowRef<CoverFrame>();
  const sources = computed(() => [
    ...new Set(options.sources().filter((url): url is string => !!url)),
  ]);
  let sequence = 0;
  let cursor = 0;
  let disposed = false;
  const request = (source: string) => {
    if (source !== current.value?.source && source !== pending.value?.source) {
      pending.value = { key: ++sequence, source };
    }
  };
  watch(
    sources,
    (urls) => {
      if (pending.value && !urls.includes(pending.value.source)) pending.value = undefined;
      if (!urls.length) return;
      const existing = urls.indexOf(current.value?.source || "");
      cursor = Math.max(0, existing);
      if (existing < 0) request(urls[0]);
    },
    { immediate: true, flush: "sync" },
  );
  const advance = () => {
    if (pending.value || sources.value.length < 2 || !options.active()) return;
    cursor = (cursor + 1) % sources.value.length;
    request(sources.value[cursor]);
  };
  watch(
    [options.active, () => sources.value.length],
    ([active, count], _, onCleanup) => {
      if (!active || count < 2) return;
      const timer = setInterval(advance, options.intervalMs ?? 8000);
      onCleanup(() => clearInterval(timer));
    },
    { immediate: true },
  );
  const failed = (key: number) => {
    if (pending.value?.key === key) pending.value = undefined;
  };
  const ready = async (key: number, decode: () => Promise<void>) => {
    const candidate = pending.value;
    if (!candidate || candidate.key !== key) return;
    try {
      await decode();
    } catch {
      failed(key);
      return;
    }
    if (!disposed && pending.value === candidate) {
      current.value = candidate;
      pending.value = undefined;
    }
  };
  onScopeDispose(() => {
    disposed = true;
    pending.value = undefined;
  });
  return { current, pending, ready, failed };
}
