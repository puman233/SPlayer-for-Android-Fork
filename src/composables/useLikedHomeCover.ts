import { ref, watch } from "vue";

/** Home artwork is an album image, never the server's heart-composited playlist image. */
export function useLikedHomeCover(options: {
  account: () => string;
  playlistId: () => number;
  cachedCover: () => string | undefined;
  load: (playlistId: number) => Promise<string | undefined>;
}) {
  const cover = ref<string>();
  watch(
    [options.account, options.playlistId, options.cachedCover],
    async ([, playlistId, cached], _, onCleanup) => {
      let current = true;
      onCleanup(() => {
        current = false;
      });
      cover.value = cached;
      if (!playlistId || cached) return;
      try {
        const loaded = await options.load(playlistId);
        if (current) cover.value = loaded;
      } catch {
        // Keep the default artwork on failure; never restore the heart composite.
        if (current) console.warn("Liked home album artwork unavailable");
      }
    },
    { immediate: true, flush: "sync" },
  );
  return cover;
}
