// Toàn bộ cấu hình đọc từ file .env (biến phải bắt đầu bằng VITE_)
const DEFAULT_API = "https://carparkingsmart.onrender.com/api";
export const API_URL = (import.meta.env.VITE_API_URL || DEFAULT_API).replace(/\/$/, "");
export const SERVER_ORIGIN = API_URL.replace(/\/api$/, "");
export const REFRESH_SECONDS = Number(import.meta.env.VITE_REFRESH_SECONDS || 30);

// Tâm bản đồ mặc định (Hà Nội). Đổi theo khu vực của bạn.
export const DEFAULT_CENTER = [21.0285, 105.8542];
export const DEFAULT_ZOOM = 12;

// Trạng thái trạm được suy ra từ số khung giờ còn trống (server trả real_available_time_slots)
export const STATUS = {
  free: { label: "Còn chỗ", color: "#12a150" },
  busy: { label: "Hết chỗ", color: "#d9480f" },
};

export const BOOKING_STATUS = {
  Quick_Booking: { label: "Chờ thanh toán", icon: "⏳", tone: "warn" },
  Confirmed: { label: "Đã xác nhận", icon: "✅", tone: "ok" },
  Completed: { label: "Đã sạc xong", icon: "🏁", tone: "ok" },
  Cancelled: { label: "Đã hủy / hết hạn", icon: "❌", tone: "bad" },
};

export const SLOT_HOURS = 2;

// Các nguồn bản đồ miễn phí, KHÔNG cần API key. Web thử lần lượt từ trên xuống,
// nguồn nào lỗi sẽ tự chuyển nguồn kế tiếp. Có thể chọn tay ở ô "Nguồn bản đồ".
const MAPTILER_KEY = import.meta.env.VITE_MAPTILER_KEY || "";

export const TILE_PROVIDERS = [
  ...(MAPTILER_KEY
    ? [{
        name: "MapTiler",
        url: `https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=${MAPTILER_KEY}`,
        subdomains: "abc",
        attribution:
          '&copy; <a href="https://www.maptiler.com/copyright/">MapTiler</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }]
    : []),
  {
    name: "Esri",
    url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
    subdomains: "abc",
    attribution: "Tiles &copy; Esri &mdash; Esri, HERE, Garmin, OpenStreetMap contributors",
  },
  {
    name: "OpenStreetMap",
    url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    subdomains: "abc",
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  },
  {
    name: "OSM Humanitarian",
    url: "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
    subdomains: "abc",
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>, HOT',
  },
];
