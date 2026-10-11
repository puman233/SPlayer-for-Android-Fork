import request from "@/utils/request";

const LOGIN_REQUEST_TIMEOUT = 30000;

export const qrKey = () => {
  return request({
    url: "/login/qr/key",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      noCookie: true,
      timestamp: Date.now(),
    },
  });
};

export const qrCreate = (key: string, qrimg: boolean = true) => {
  return request({
    url: "/login/qr/create",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      key,
      qrimg,
      noCookie: true,
      timestamp: Date.now(),
    },
  });
};

export const checkQr = (key: string) => {
  return request({
    url: "/login/qr/check",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      key,
      noCookie: true,
      timestamp: Date.now(),
    },
  });
};

export const loginPhone = (phone: number, captcha: number, ctcode: number = 86) => {
  return request({
    url: "/login/cellphone",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      phone,
      captcha,
      ctcode,
      noCookie: true,
      timestamp: Date.now(),
    },
  });
};

export const sentCaptcha = (phone: number, ctcode: number = 86) => {
  return request({
    url: "/captcha/sent",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      phone,
      ctcode,
      noCookie: true,
      timestamp: Date.now(),
    },
  });
};

export const verifyCaptcha = (phone: number, captcha: number, ctcode: number = 86) => {
  return request({
    url: "/captcha/verify",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      phone,
      captcha,
      ctcode,
      timestamp: Date.now(),
    },
  });
};

export const getLoginState = () => {
  return request({
    url: "/login/status",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      timestamp: Date.now(),
    },
  });
};

export const refreshLogin = () => {
  return request({
    url: "/login/refresh",
    timeout: LOGIN_REQUEST_TIMEOUT,
    params: {
      timestamp: Date.now(),
    },
  });
};

export const logout = (cookie?: string) => {
  return request({
    url: "/logout",
    timeout: 5000,
    headers: cookie ? { "X-SPlayer-Cookie": `MUSIC_U=${cookie};os=pc;` } : undefined,
    "axios-retry": { retries: 0 },
    params: {
      noCookie: true,
      timestamp: Date.now(),
    },
  });
};

export const countryList = () => {
  return request({
    url: "/countries/code/list",
    timeout: LOGIN_REQUEST_TIMEOUT,
  });
};
