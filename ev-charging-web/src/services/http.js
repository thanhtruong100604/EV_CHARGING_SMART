import { API_URL } from "../config";

const KEY = "ev_auth";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) || null;
  } catch {
    return null;
  }
}

let session = load();
let onUnauthorized = () => {};

export const getSession = () => session;
export function setSession(s) {
  session = s;
  try {
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  } catch {
    /* trình duyệt chặn localStorage: bỏ qua, vẫn dùng được trong phiên */
  }
}
export const setUnauthorizedHandler = (fn) => {
  onUnauthorized = fn;
};

export class ApiError extends Error {
  constructor(message, status = 0, data = null) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

function messageFrom(data, status) {
  if (data && typeof data === "object") {
    if (data.error) return data.error;
    if (data.detail) return data.detail;
    const first = Object.values(data)[0];
    if (Array.isArray(first) && first[0]) return String(first[0]);
  }
  if (typeof data === "string" && data && data.length < 200) return data;
  return `Lỗi máy chủ (${status})`;
}

/**
 * Gọi API Django. Render gói miễn phí "ngủ" khi không có người dùng, lần gọi đầu có thể mất 30–60 giây,
 * nên: timeout dài (60s) và tự thử lại 2 lần với các request đọc (GET).
 */
async function request(method, path, { body, query, timeout = 60000 } = {}) {
  const url = new URL(API_URL + path);
  Object.entries(query || {}).forEach(([k, v]) => v != null && url.searchParams.set(k, v));
  const headers = { Accept: "application/json" };
  if (body) headers["Content-Type"] = "application/json";
  if (session?.token) headers.Authorization = `Token ${session.token}`;
  const canRetry = method === "GET";

  for (let attempt = 0; ; attempt++) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeout);
    try {
      const res = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        signal: ctrl.signal,
      });
      clearTimeout(timer);
      const text = await res.text();
      let data = null;
      try {
        data = text ? JSON.parse(text) : null;
      } catch {
        data = text;
      }
      if (res.status === 401 && session) {
        setSession(null);
        onUnauthorized();
      }
      if ([502, 503, 504].includes(res.status) && canRetry && attempt < 2) {
        await sleep(2500);
        continue;
      }
      if (!res.ok) throw new ApiError(messageFrom(data, res.status), res.status, data);
      return data;
    } catch (e) {
      clearTimeout(timer);
      if (e instanceof ApiError) throw e;
      if (canRetry && attempt < 2) {
        await sleep(2500);
        continue;
      }
      throw new ApiError(
        "Không kết nối được máy chủ. Máy chủ có thể đang khởi động, hãy thử lại sau ít giây.",
        0
      );
    }
  }
}

export const http = {
  get: (path, query, opts) => request("GET", path, { query, ...opts }),
  post: (path, body, opts) => request("POST", path, { body: body ?? {}, ...opts }),
};
