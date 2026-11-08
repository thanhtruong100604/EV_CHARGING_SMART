// Khoảng cách đường chim bay (km) giữa 2 toạ độ – công thức Haversine
export function distanceKm(lat1, lng1, lat2, lng2) {
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(a));
}

export const formatVnd = (n) => new Intl.NumberFormat("vi-VN").format(Math.round(Number(n) || 0)) + " đ";
export const formatDateTime = (iso) => (iso ? new Date(iso).toLocaleString("vi-VN") : "—");
export const pad = (n) => String(n).padStart(2, "0");
export const hourLabel = (h) => `${pad(h)}:00 – ${pad(h + 2)}:00`;
export const formatClock = (sec) => `${pad(Math.floor(sec / 60))}:${pad(sec % 60)}`;

/** Thời gian giữ chỗ (phút) khi "Đặt ngay": giống app — 0,12 giây/mét + 5 phút đệm, tối thiểu 5, tối đa 60. */
export function estimateHoldMinutes(distKm) {
  if (distKm == null) return 15;
  const seconds = Math.max(120, distKm * 1000 * 0.12);
  return Math.min(60, Math.max(5, Math.floor(seconds / 60) + 5));
}
