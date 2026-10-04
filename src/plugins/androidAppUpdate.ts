import { registerPlugin, type PluginListenerHandle } from "@capacitor/core";

export interface AndroidUpdateProgressEvent {
  bytesRead: number;
  contentLength: number;
  percent: number;
}

interface AndroidAppUpdatePlugin {
  getSupportedAbis(): Promise<{ abis: string[] }>;
  downloadApk(options: {
    url: string;
    fileName: string;
    sha256?: string;
    size?: number;
  }): Promise<{ fileName: string; bytesRead: number; sha256: string }>;
  canInstallApk(): Promise<{ allowed: boolean }>;
  getDownloadedApk(options: {
    fileName: string;
    sha256: string;
  }): Promise<{ available: boolean; bytesRead: number }>;
  cancelDownload(): Promise<void>;
  installApk(options: { fileName: string }): Promise<{ needsPermission: boolean }>;
  addListener(
    eventName: "downloadProgress",
    listener: (event: AndroidUpdateProgressEvent) => void,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

export const AndroidAppUpdate = registerPlugin<AndroidAppUpdatePlugin>("AndroidAppUpdate");
