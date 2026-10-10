<template>
  <div class="login-qrcode">
    <div class="qr-img">
      <div
        v-if="qrImg"
        :class="['qr', { success: qrStatusCode === 802, error: qrStatusCode === 800 }]"
      >
        <n-qr-code
          :value="qrImg"
          :size="160"
          :icon-size="30"
          icon-src="/icons/favicon.png?asset"
          error-correction-level="H"
        />
        <Transition name="fade" mode="out-in">
          <div v-if="loginName" class="login-data">
            <n-image
              :src="loginAvatar.replace(/^http:/, 'https:') + '?param=100y100'"
              class="cover"
              preview-disabled
              @load="coverLoaded"
            >
              <template #placeholder>
                <div class="cover-loading">
                  <img src="/images/avatar.jpg?asset" class="loading-img" alt="loading-img" />
                </div>
              </template>
            </n-image>
            <n-text>{{ loginName }}</n-text>
          </div>
        </Transition>
      </div>
      <n-skeleton v-else class="qr" />
    </div>
    <n-text class="tip" depth="3">{{ qrTipText }}</n-text>
  </div>
</template>

<script setup lang="ts">
import { qrKey, checkQr } from "@/api/login";
import { LoginType } from "@/types/main";
import { coverLoaded } from "@/utils/helper";

const props = defineProps<{
  pause?: boolean;
}>();

const emit = defineEmits<{
  saveLogin: [any, LoginType];
}>();

const qrCodeTip = {
  800: "二维码已过期，即将重试",
  801: "请打开网易云音乐 App 扫码登录",
  802: "扫描成功，请在客户端确认登录",
  803: "登录成功",
} as const;

const qrImg = ref<string>("");
const qrUnikey = ref<string>("");
const qrStatusCode = ref<keyof typeof qrCodeTip>(801);
const qrChecking = ref(false);

const qrTipText = computed(() => {
  return qrCodeTip[qrStatusCode.value] || "遇到未知状态，请重试";
});

const loginName = ref<string>("");
const loginAvatar = ref<string>("");

const getQrData = async () => {
  try {
    pauseCheck();
    qrChecking.value = false;
    qrStatusCode.value = 801;
    loginName.value = "";
    loginAvatar.value = "";

    const res = await qrKey();
    const unikey = res?.data?.unikey;
    if (!unikey) return;

    qrImg.value = `https://music.163.com/login?codekey=${unikey}`;
    qrUnikey.value = unikey;
    resumeCheck();
  } catch (error) {
    pauseCheck();
    console.error("二维码获取失败:");
  }
};

const checkQrStatus = async () => {
  if (!qrUnikey.value || props.pause || qrChecking.value) return;

  qrChecking.value = true;
  try {
    const result = await checkQr(qrUnikey.value);
    if (!result) return;

    const { code, cookie, nickname, avatarUrl } = result;
    switch (code) {
      case 800:
        qrStatusCode.value = 800;
        void getQrData();
        break;
      case 801:
        qrStatusCode.value = 801;
        break;
      case 802:
        qrStatusCode.value = 802;
        loginName.value = nickname;
        loginAvatar.value = avatarUrl;
        break;
      case 803:
        qrStatusCode.value = 803;
        pauseCheck();
        if (cookie && cookie.includes("MUSIC_U")) {
          emit("saveLogin", { code: 200, cookie }, "qr");
        } else {
          window.$message.error("登录出错，请重试");
          void getQrData();
        }
        break;
      default:
        break;
    }
  } catch (error) {
    console.error("二维码状态检查失败:");
  } finally {
    qrChecking.value = false;
  }
};

const { pause: pauseCheck, resume: resumeCheck } = useIntervalFn(checkQrStatus, 1000, {
  immediate: false,
});

onMounted(getQrData);
onBeforeUnmount(pauseCheck);
</script>

<style lang="scss" scoped>
.login-qrcode {
  display: flex;
  flex-direction: column;
  align-items: center;
  .qr-img {
    display: flex;
    margin: 20px 0;
    width: 180px;
    height: 180px;
    border-radius: 12px;
    overflow: hidden;
    .qr {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      .n-qr-code {
        padding: 0;
        height: 180px;
        width: 180px;
        min-height: 180px;
        min-width: 180px;
        transition:
          opacity 0.3s,
          filter 0.3s;
        :deep(canvas) {
          width: 100% !important;
          height: 100% !important;
        }
      }
      .login-data {
        position: absolute;
        display: flex;
        flex-direction: column;
        align-items: center;
        z-index: 1;
        .cover {
          width: 40px;
          height: 40px;
          border-radius: 50%;
          overflow: hidden;
          margin-bottom: 8px;
        }
      }
      &.success {
        .n-qr-code {
          opacity: 0.5;
          filter: blur(4px);
        }
      }
    }
  }
  .tip {
    margin-bottom: 12px;
  }
}
</style>
