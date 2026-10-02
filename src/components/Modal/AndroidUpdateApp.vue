<template>
  <div class="android-update-app">
    <n-flex align="center" :wrap="false">
      <n-tag type="primary">v{{ packageJson.version }}</n-tag>
      <SvgIcon name="Right" />
      <n-tag type="warning">{{ data.version }}</n-tag>
    </n-flex>

    <n-alert v-if="data.prerelease" type="warning" :bordered="false">
      这是预发布版本，可能包含未完成的功能或已知问题。
    </n-alert>
    <n-alert v-if="errorMessage" type="error" :bordered="false">{{ errorMessage }}</n-alert>
    <n-alert v-else-if="asset" type="info" :bordered="false">
      已选择 {{ asset.abi }} 安装包：{{ asset.name }}（{{ formatAssetSize(asset.size) }}）
    </n-alert>
    <n-spin v-else size="small" description="正在识别设备安装包" />

    <n-progress
      v-if="downloading || downloaded"
      type="line"
      :percentage="Math.max(0, progress)"
      :indicator-placement="'inside'"
      processing
    />

    <n-scrollbar style="max-height: 360px">
      <div class="markdown-body" v-html="data.changelog || '暂无更新日志'" />
    </n-scrollbar>

    <n-flex justify="end" class="actions">
      <n-button secondary @click="emit('close')">关闭</n-button>
      <n-button secondary @click="openLink(data.url)">浏览器下载</n-button>
      <n-button v-if="downloading" type="warning" secondary @click="cancelDownload">
        取消下载
      </n-button>
      <n-button v-else-if="downloaded" type="success" @click="installUpdate">
        调用系统安装器
      </n-button>
      <n-button v-else type="primary" :disabled="!asset" @click="downloadUpdate">
        下载并校验
      </n-button>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import type { PluginListenerHandle } from "@capacitor/core";
import packageJson from "@/../package.json";
import type { UpdateLogType } from "@/types/main";
import { openLink } from "@/utils/helper";
import { AndroidAppUpdate } from "@/plugins/androidAppUpdate";
import {
  fetchAndroidReleaseAssets,
  formatAssetSize,
  selectAndroidApkAsset,
  type AndroidReleaseAsset,
} from "@/core/update/assets";

import "github-markdown-css/github-markdown.css";

const props = defineProps<{ data: UpdateLogType }>();
const emit = defineEmits<{ close: [] }>();

const asset = ref<AndroidReleaseAsset | null>(null);
const errorMessage = ref("");
const downloading = ref(false);
const downloaded = ref(false);
const progress = ref(0);
let progressListener: PluginListenerHandle | null = null;

const resolveAsset = async () => {
  try {
    const [{ abis }, assets] = await Promise.all([
      AndroidAppUpdate.getSupportedAbis(),
      fetchAndroidReleaseAssets(props.data.version),
    ]);
    asset.value = selectAndroidApkAsset(assets, abis);
    if (!asset.value) errorMessage.value = "该版本没有适合本设备且带 SHA-256 摘要的 APK";
  } catch (error) {
    console.error("识别 Android 更新包失败", error);
    errorMessage.value = "无法读取版本附件，请检查网络后重试";
  }
};

const downloadUpdate = async () => {
  if (!asset.value) return;
  errorMessage.value = "";
  progress.value = 0;
  downloaded.value = false;
  downloading.value = true;
  try {
    await AndroidAppUpdate.downloadApk({
      url: asset.value.url,
      fileName: asset.value.name,
      sha256: asset.value.sha256,
    });
    downloaded.value = true;
    progress.value = 100;
    window.$message.success("安装包下载并校验完成");
  } catch (error) {
    const message = String((error as { message?: string })?.message || error);
    const code = String((error as { code?: string })?.code || "");
    if (code !== "UPDATE_DOWNLOAD_CANCELLED") {
      console.error("Android 更新包下载失败", error);
      errorMessage.value =
        code === "UPDATE_CHECKSUM_MISMATCH" || message.includes("SHA-256")
          ? "安装包校验失败，文件已删除，请重试"
          : "安装包下载失败，请检查网络后重试";
    }
  } finally {
    downloading.value = false;
  }
};

const cancelDownload = async () => {
  await AndroidAppUpdate.cancelDownload();
};

const installUpdate = async () => {
  if (!asset.value) return;
  try {
    const result = await AndroidAppUpdate.installApk({ fileName: asset.value.name });
    if (result.needsPermission) {
      window.$message.info("请允许此来源安装应用，然后返回并再次点击安装");
    }
  } catch (error) {
    console.error("调用 Android 系统安装器失败", error);
    const code = String((error as { code?: string })?.code || "");
    const message =
      code === "UPDATE_PACKAGE_MISMATCH"
        ? "安装包与当前应用不匹配，已阻止安装"
        : code === "UPDATE_VERSION_NOT_NEWER"
          ? "安装包版本不高于当前版本，已阻止安装"
          : code === "UPDATE_SIGNATURE_MISMATCH"
            ? "安装包签名与当前应用不兼容，已阻止安装"
            : code === "UPDATE_APK_INVALID"
              ? "安装包无法解析，已阻止安装"
              : "无法打开系统安装器，请稍后重试";
    window.$message.error(message);
  }
};

onMounted(async () => {
  progressListener = await AndroidAppUpdate.addListener("downloadProgress", (event) => {
    if (event.percent >= 0) progress.value = Number(event.percent.toFixed(1));
  });
  await resolveAsset();
});

onBeforeUnmount(() => {
  progressListener?.remove();
  if (downloading.value) AndroidAppUpdate.cancelDownload();
});
</script>

<style lang="scss" scoped>
.android-update-app {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .actions {
    margin-top: 4px;
  }
}
</style>
