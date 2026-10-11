import { requestFailureCategory } from "@/utils/requestDiagnostics";
/**
 * 流媒体 Store
 * 管理流媒体服务器配置和数据缓存
 */

import type {
  StreamingServerConfig,
  StreamingConnectionStatus,
  StreamingAlbumType,
  StreamingArtistType,
  StreamingPlaylistType,
} from "@/types/streaming";
import { SongType } from "@/types/main";
import { subsonic, jellyfin, emby, webdav } from "@/api/streaming";
import localforage from "localforage";

// localforage 实例延迟初始化，避免模块解析阶段创建 IndexedDB 连接阻塞冷启动
let _streamingDB: ReturnType<typeof localforage.createInstance> | null = null;
const getStreamingDB = () => {
  if (!_streamingDB) {
    _streamingDB = localforage.createInstance({
      name: "streaming-data",
      description: "Streaming media server data",
      storeName: "streaming",
    });
  }
  return _streamingDB;
};

/**
 * 生成唯一 ID
 */
const generateId = (): string => {
  // 使用时间戳 + 随机数生成简单的唯一 ID
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * 创建流媒体 Store
 */
const createStreamingStore = () => {
  let connectionRevision = 0;
  let connecting = false;
  let saving: Promise<void> = Promise.resolve();
  const sessionRevision = ref(0);
  const pendingRequests = new Set<symbol>();
  const latestRequests = new Map<string, symbol>();
  // 响应式状态
  const servers = ref<StreamingServerConfig[]>([]);
  const activeServerId = ref<string | null>(null);
  const connectionStatus = ref<StreamingConnectionStatus>({ connected: false });
  const loading = ref(false);
  const songs = ref<SongType[]>([]);
  const artists = ref<StreamingArtistType[]>([]);
  const albums = ref<StreamingAlbumType[]>([]);
  const playlists = ref<StreamingPlaylistType[]>([]);

  // 计算属性：当前激活的服务器配置
  const activeServer = computed<StreamingServerConfig | null>(() => {
    if (!activeServerId.value) return null;
    return servers.value.find((s) => s.id === activeServerId.value) || null;
  });

  const beginRequest = (kind: string, tracksLoading = true) => {
    const revision = connectionRevision;
    const token = Symbol(kind);
    const server = activeServer.value ? { ...activeServer.value } : null;
    latestRequests.set(kind, token);
    if (tracksLoading) { pendingRequests.add(token); loading.value = true; }
    return {
      server,
      current: () => revision === connectionRevision && latestRequests.get(kind) === token,
      finish: () => {
        if (revision !== connectionRevision) return;
        pendingRequests.delete(token);
        if (tracksLoading) loading.value = connecting || pendingRequests.size > 0;
      },
    };
  };

  const getCacheScope = (): string | null => activeServer.value?.cacheScope || null;

  // 计算属性：是否已连接
  const isConnected = computed(() => connectionStatus.value.connected);

  // 计算属性：是否已配置服务器
  const hasServer = computed(() => servers.value.length > 0);

  /**
   * 加载服务器配置
   */
  const loadServers = async (): Promise<void> => {
    const revision = connectionRevision;
    try {
      const savedServers = await getStreamingDB().getItem<StreamingServerConfig[]>("servers");
      if (revision !== connectionRevision) return;
      if (savedServers && servers.value.length === 0) {
        servers.value = savedServers;
      }

      const savedActiveId = await getStreamingDB().getItem<string>("activeServerId");
      if (revision !== connectionRevision) return;
      if (savedActiveId && servers.value.some((s) => s.id === savedActiveId)) {
        activeServerId.value = savedActiveId;
      }

      // 自动连接
      if (servers.value.length > 0 && activeServerId.value) {
        await connectToServer(activeServerId.value);
      }
    } catch (error) {
      console.error("Failed to load streaming servers:", requestFailureCategory(error));
    }
  };

  /**
   * 保存服务器配置
   */
  const saveServers = (): Promise<void> => {
    const serversData = JSON.parse(JSON.stringify(servers.value));
    const activeId = activeServerId.value;
    saving = saving.catch(() => {}).then(async () => {
      await getStreamingDB().setItem("servers", serversData);
      await getStreamingDB().setItem("activeServerId", activeId);
    });
    return saving.catch((error) => {
      console.error("Failed to save streaming servers:", requestFailureCategory(error));
    });
  };
  /**
   * 添加服务器配置
   */
  const addServer = async (
    config: Omit<StreamingServerConfig, "id">,
  ): Promise<StreamingServerConfig> => {
    const newServer: StreamingServerConfig = {
      ...config,
      id: generateId(),
      cacheScope: crypto.randomUUID(),
    };

    servers.value.push(newServer);
    await saveServers();

    return newServer;
  };

  /**
   * 更新服务器配置
   */
  const updateServer = async (
    id: string,
    updates: Partial<StreamingServerConfig>,
  ): Promise<boolean> => {
    const index = servers.value.findIndex((s) => s.id === id);
    if (index === -1) return false;

    const previous = servers.value[index];
    const next = { ...previous, ...updates, id: previous.id };
    const identityKeys = ["type", "url", "username", "password", "userId", "libraryRoot", "webdavAuth"] as const;
    if (identityKeys.some((key) => previous[key] !== next[key])) {
      next.cacheScope = crypto.randomUUID();
      if (activeServerId.value === id) {
        ++connectionRevision;
        sessionRevision.value = connectionRevision;
        connecting = false;
        pendingRequests.clear(); latestRequests.clear();
        loading.value = false;
        connectionStatus.value = { connected: false };
        clearCache();
      }
    }
    servers.value[index] = next;
    await saveServers();

    return true;
  };

  /**
   * 删除服务器配置
   */
  const removeServer = async (id: string): Promise<boolean> => {
    const index = servers.value.findIndex((s) => s.id === id);
    if (index === -1) return false;

    servers.value.splice(index, 1);

    // 如果删除的是当前激活的服务器，清除激活状态
    if (activeServerId.value === id) {
      disconnect();
    }

    await saveServers();
    return true;
  };

  /**
   * 测试服务器连接
   */
  const testConnection = async (
    config: StreamingServerConfig,
  ): Promise<StreamingConnectionStatus> => {
    try {
      if (config.type === "jellyfin") {
        // Jellyfin 需要先认证
        const authResult = await jellyfin.authenticate(config);
        config.accessToken = authResult.accessToken;
        config.userId = authResult.userId;

        const pingResult = await jellyfin.ping(config);
        return {
          connected: true,
          serverName: config.name,
          serverVersion: pingResult.version,
        };
      } else if (config.type === "emby") {
        // Emby 需要先认证
        const authResult = await emby.authenticate(config);
        config.accessToken = authResult.accessToken;
        config.userId = authResult.userId;

        const pingResult = await emby.ping(config);
        return {
          connected: true,
          serverName: config.name,
          serverVersion: pingResult.version,
        };
      } else if (config.type === "webdav") {
        // WebDAV：用 PROPFIND 库根做连通性测试
        const pingResult = await webdav.ping(config);
        return {
          connected: true,
          serverName: config.name,
          serverVersion: pingResult.version,
        };
      } else {
        // Subsonic API (Navidrome / OpenSubsonic)
        const pingResult = await subsonic.ping(config);
        return {
          connected: true,
          serverName: config.name,
          serverVersion: pingResult.serverVersion || pingResult.version,
        };
      }
    } catch {
      return {
        connected: false,
        error: "连接失败，请检查服务器地址、凭据和网络",
      };
    }
  };

  /**
   * 连接到服务器
   */
  const connectToServer = async (serverId: string): Promise<boolean> => {
    const savedServer = servers.value.find((s) => s.id === serverId);
    if (!savedServer) return false;
    const revision = ++connectionRevision;
    sessionRevision.value = revision;
    if (!savedServer.cacheScope) savedServer.cacheScope = crypto.randomUUID();
    pendingRequests.clear();
    latestRequests.clear();
    connecting = true;
    // Authentication may mutate this snapshot, never the live configuration.
    const server = { ...savedServer };
    activeServerId.value = serverId;
    clearCache();
    // Persist selection and namespace even when authentication is offline.
    void saveServers();

    loading.value = true;
    connectionStatus.value = { connected: false };

    try {
      const status = await testConnection(server);
      if (revision !== connectionRevision) return false;

      if (status.connected) {
        activeServerId.value = serverId;
        server.lastConnected = Date.now();

        const live = servers.value.find((s) => s.id === serverId);
        if (!live) return false;
        if (live.userId !== server.userId) live.cacheScope = crypto.randomUUID();
        Object.assign(live, { accessToken: server.accessToken, userId: server.userId, lastConnected: server.lastConnected });
        // Synchronous connection observers may immediately start authenticated reads.
        connectionStatus.value = status;
        await saveServers();

        return revision === connectionRevision;
      }

      connectionStatus.value = status;
      return false;
    } catch {
      if (revision !== connectionRevision) return false;
      connectionStatus.value = {
        connected: false,
        error: "连接失败，请检查服务器地址、凭据和网络",
      };
      return false;
    } finally {
      if (revision === connectionRevision) {
        connecting = false;
        loading.value = pendingRequests.size > 0;
      }
    }
  };

  /**
   * 断开连接
   */
  const disconnect = (): void => {
    ++connectionRevision;
    sessionRevision.value = connectionRevision;
    connecting = false;
    pendingRequests.clear();
    latestRequests.clear();
    loading.value = false;
    activeServerId.value = null;
    connectionStatus.value = { connected: false };
    clearCache();
    void saveServers();
  };

  /**
   * 清除缓存
   */
  const clearCache = (): void => {
    songs.value = [];
    artists.value = [];
    albums.value = [];
    playlists.value = [];
  };

  /**
   * 获取随机歌曲
   */
  const fetchRandomSongs = async (count: number = 50): Promise<SongType[]> => {
    const request = beginRequest("songs");
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      let result: SongType[];

      if (server.type === "jellyfin") {
        result = await jellyfin.getRandomSongs(server, count);
      } else if (server.type === "emby") {
        result = await emby.getRandomSongs(server, count);
      } else if (server.type === "webdav") {
        result = await webdav.getRandomSongs(server, count);
      } else {
        result = await subsonic.getRandomSongs(server, count);
      }

      if (!request.current()) return [];
      songs.value = result;
      return result;
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch random songs:", requestFailureCategory(error));
      return [];
    } finally {
      request.finish();
    }
  };

  /**
   * 获取歌曲列表（支持分页）
   * @param offset 偏移量
   * @param size 数量
   * @param append 是否追加到现有列表
   */
  const fetchSongs = async (
    offset: number = 0,
    size: number = 50,
    append: boolean = false,
  ): Promise<SongType[]> => {
    const request = beginRequest("songs");
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      let result: SongType[];

      if (server.type === "jellyfin") {
        result = await jellyfin.getSongs(server, offset, size);
      } else if (server.type === "emby") {
        result = await emby.getSongs(server, offset, size);
      } else if (server.type === "webdav") {
        result = await webdav.getSongs(server, offset, size);
      } else {
        result = await subsonic.getSongs(server, offset, size);
      }

      if (!request.current()) return [];
      if (append) {
        songs.value = [...songs.value, ...result];
      } else {
        songs.value = result;
      }
      return result;
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch songs:", requestFailureCategory(error));
      throw error;
    } finally {
      request.finish();
    }
  };

  /**
   * 获取艺术家列表
   */
  const fetchArtists = async (): Promise<StreamingArtistType[]> => {
    const request = beginRequest("artists");
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      let result: StreamingArtistType[];

      if (server.type === "jellyfin") {
        result = await jellyfin.getArtists(server);
      } else if (server.type === "emby") {
        result = await emby.getArtists(server);
      } else if (server.type === "webdav") {
        result = await webdav.getArtists(server);
      } else {
        result = await subsonic.getArtists(server);
      }

      if (!request.current()) return [];
      artists.value = result;
      return result;
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch artists:", requestFailureCategory(error));
      return [];
    } finally {
      request.finish();
    }
  };

  /**
   * 获取专辑列表
   */
  const fetchAlbums = async (): Promise<StreamingAlbumType[]> => {
    const request = beginRequest("albums");
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      let result: StreamingAlbumType[];

      if (server.type === "jellyfin") {
        result = await jellyfin.getAlbums(server);
      } else if (server.type === "emby") {
        result = await emby.getAlbums(server);
      } else if (server.type === "webdav") {
        result = await webdav.getAlbums(server);
      } else {
        result = await subsonic.getAlbumList(server, "alphabeticalByName");
      }

      if (!request.current()) return [];
      albums.value = result;
      return result;
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch albums:", requestFailureCategory(error));
      return [];
    } finally {
      request.finish();
    }
  };

  /**
   * 获取歌单列表
   */
  const fetchPlaylists = async (): Promise<StreamingPlaylistType[]> => {
    const request = beginRequest("playlists");
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      let result: StreamingPlaylistType[];

      if (server.type === "jellyfin") {
        result = await jellyfin.getPlaylists(server);
      } else if (server.type === "emby") {
        result = await emby.getPlaylists(server);
      } else if (server.type === "webdav") {
        result = await webdav.getPlaylists(server);
      } else {
        result = await subsonic.getPlaylists(server);
      }

      if (!request.current()) return [];
      playlists.value = result;
      return result;
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch playlists:", requestFailureCategory(error));
      return [];
    } finally {
      request.finish();
    }
  };

  /**
   * 获取专辑歌曲
   */
  const fetchAlbumSongs = async (albumId: string): Promise<SongType[]> => {
    const request = beginRequest("fetchAlbumSongs", false);
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      if (server.type === "jellyfin") {
        const result = await jellyfin.getAlbumItems(server, albumId);
        return request.current() ? result : [];
      } else if (server.type === "emby") {
        const result = await emby.getAlbumItems(server, albumId);
        return request.current() ? result : [];
      } else if (server.type === "webdav") {
        const result = await webdav.getAlbumItems(server, albumId);
        return request.current() ? result : [];
      } else {
        const result = await subsonic.getAlbum(server, albumId);
        return request.current() ? result.songs : [];
      }
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch album songs:", requestFailureCategory(error));
      return [];
    } finally {
      request.finish();
    }
  };

  /**
   * 获取歌单歌曲
   */
  const fetchPlaylistSongs = async (playlistId: string): Promise<SongType[]> => {
    const request = beginRequest("fetchPlaylistSongs", false);
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return []; }

    try {
      if (server.type === "jellyfin") {
        const result = await jellyfin.getPlaylistItems(server, playlistId);
        return request.current() ? result : [];
      } else if (server.type === "emby") {
        const result = await emby.getPlaylistItems(server, playlistId);
        return request.current() ? result : [];
      } else if (server.type === "webdav") {
        const result = await webdav.getPlaylistItems(server, playlistId);
        return request.current() ? result : [];
      } else {
        const result = await subsonic.getPlaylist(server, playlistId);
        return request.current() ? result.songs : [];
      }
    } catch (error) {
      if (!request.current()) return [];
      console.error("Failed to fetch playlist songs:", requestFailureCategory(error));
      return [];
    } finally {
      request.finish();
    }
  };

  /**
   * 搜索
   */
  const search = async (
    query: string,
  ): Promise<{
    artists: StreamingArtistType[];
    albums: StreamingAlbumType[];
    songs: SongType[];
  }> => {
    const request = beginRequest("search", false);
    const server = request.server;
    if (!server || !isConnected.value) {
      request.finish();
      return { artists: [], albums: [], songs: [] };
    }

    try {
      if (server.type === "jellyfin") {
        const result = await jellyfin.search(server, query);
        return request.current() ? result : { artists: [], albums: [], songs: [] };
      } else if (server.type === "emby") {
        const result = await emby.search(server, query);
        return request.current() ? result : { artists: [], albums: [], songs: [] };
      } else if (server.type === "webdav") {
        const result = await webdav.search(server, query);
        return request.current() ? result : { artists: [], albums: [], songs: [] };
      } else {
        const result = await subsonic.search(server, query);
        return request.current() ? result : { artists: [], albums: [], songs: [] };
      }
    } catch (error) {
      if (!request.current()) return { artists: [], albums: [], songs: [] };
      console.error("Failed to search:", requestFailureCategory(error));
      return { artists: [], albums: [], songs: [] };
    } finally {
      request.finish();
    }
  };

  /**
   * 获取歌词
   */
  const fetchLyrics = async (song: SongType): Promise<string> => {
    const request = beginRequest("fetchLyrics", false);
    const server = request.server;
    if (!server || !isConnected.value) { request.finish(); return ""; }

    try {
      if (server.type === "jellyfin" && song.originalId) {
        const result = await jellyfin.getLyrics(server, song.originalId);
        return request.current() ? result : "";
      } else if (server.type === "emby" && song.originalId) {
        const result = await emby.getLyrics(server, song.originalId);
        return request.current() ? result : "";
      } else if (server.type === "webdav") {
        // WebDAV 没有歌词接口
        return "";
      } else {
        // 优先使用 ID 获取
        if (song.originalId) {
          const lyrics = await subsonic.getLyricsBySongId(server, song.originalId);
          if (lyrics) return request.current() ? lyrics : "";
        }
        return "";
      }
    } catch (error) {
      if (!request.current()) return "";
      console.error("Failed to fetch lyrics:", requestFailureCategory(error));
      return "";
    } finally {
      request.finish();
    }
  };

  /**
   * 获取流媒体歌曲播放地址
   */
  const getSongUrl = (song: SongType): string => {
    if (song.type !== "streaming" || !song.serverId) return song.streamUrl || "";

    const server = servers.value.find((s) => s.id === song.serverId);
    if (!server) return song.streamUrl || "";

    if (server.type === "jellyfin" && server.accessToken && song.originalId) {
      return jellyfin.getAudioStreamUrl(server, song.originalId);
    }

    if (server.type === "emby" && server.accessToken && song.originalId) {
      return emby.getAudioStreamUrl(server, song.originalId);
    }

    if (server.type === "webdav" && song.originalId) {
      return webdav.getStreamUrl(server, song.originalId);
    }

    return song.streamUrl || "";
  };

  // 初始化：加载保存的配置
  loadServers();

  return {
    // 状态
    servers,
    activeServerId,
    activeServer,
    connectionStatus,
    isConnected,
    hasServer,
    loading,
    songs,
    artists,
    albums,
    playlists,

    // 方法
    loadServers,
    saveServers,
    addServer,
    updateServer,
    removeServer,
    testConnection,
    connectToServer,
    disconnect,
    clearCache,
    fetchRandomSongs,
    fetchSongs,
    fetchArtists,
    fetchAlbums,
    fetchPlaylists,
    fetchAlbumSongs,
    fetchPlaylistSongs,
    search,
    fetchLyrics,
    getSongUrl,
    getCacheScope,
    sessionRevision,
  };
};

// 创建全局实例
const streamingStoreInstance = createStreamingStore();

/**
 * 获取流媒体 Store 实例
 */
export const useStreamingStore = () => streamingStoreInstance;

export default useStreamingStore;
