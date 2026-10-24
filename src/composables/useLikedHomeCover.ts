import { ref, watch } from "vue";

/** Home artwork is an album image, never the server's heart-composited playlist image. */
export function useLikedHomeCover(options: {
  account: () => string;
  playlistId: () => number;
  cachedCovers: () => string[];
  load: (playlistId: number) => Promise<string[]>;
}) {
  const cover = ref<string[]>([]);
  let scope = "";
  watch(
    [options.account, options.playlistId, options.cachedCovers],
    async ([account, playlistId, cached], _, onCleanup) => {
      let current = true;
      onCleanup(() => {
        current = false;
      });
      const nextScope = `${account}:${playlistId}`;
      if (scope !== nextScope) {
        cover.value = [];
        scope = nextScope;
      }
      if (cached.length) cover.value = cached;
      if (!playlistId || cached.length) return;
      try {
        const loaded = await options.load(playlistId);
        if (current && loaded.length) cover.value = loaded;
      } catch {
        // Keep the default artwork on failure; never restore the heart composite.
        if (current) console.warn("Liked home album artwork unavailable");
      }
    },
    { immediate: true, flush: "sync" },
  );
  return cover;
}
