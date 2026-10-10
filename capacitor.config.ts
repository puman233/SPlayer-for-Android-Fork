/// <reference types="@capacitor/status-bar" />

import type { CapacitorConfig } from "@capacitor/cli";

type AndroidCapacitorConfig = CapacitorConfig & {
  /**
   * Project-level marker for Android WebView blur requirements.
   * Capacitor does not consume this key directly. Keep AndroidManifest.xml
   * synchronized with android:hardwareAccelerated="true" after `npx cap add android`.
   */
  androidHardwareAcceleration?: boolean;
};

const config: AndroidCapacitorConfig = {
  appId: "top.imsyy.splayer.android",
  appName: "SPlayer for Android",
  webDir: "dist/capacitor",
  backgroundColor: "#00000000",
  // SDK packet/Cookie tracing includes credentials. Application diagnostics use
  // their own Console client and remain enabled in Debug and production.
  loggingBehavior: "none",
  initialFocus: true,
  android: {
    backgroundColor: "#00000000",
    allowMixedContent: true,
    webContentsDebuggingEnabled: false,
    initialFocus: true,
  },
  plugins: {
    StatusBar: {
      overlaysWebView: true,
      style: "LIGHT",
      backgroundColor: "#00000000",
    },
  },
  androidHardwareAcceleration: true,
};

export default config;
