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

export const formatVnd = (n) =>
  n == null ? "—" : new Intl.NumberFormat("vi-VN").format(n) + " đ/kWh";
