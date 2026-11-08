// Định tuyến & tìm địa điểm — dùng cùng dịch vụ miễn phí với app Android.

export async function fetchRoute(from, to) {
  const url =
    "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
    `${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson`;
  const res = await fetch(url);
  if (!res.ok) throw new Error("Dịch vụ chỉ đường đang bận, hãy thử lại.");
  const json = await res.json();
  if (json.code !== "Ok" || !json.routes?.length) throw new Error("Không tìm được đường đi.");
  const r = json.routes[0];
  return {
    points: r.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
    distanceKm: r.distance / 1000,
    minutes: Math.max(1, Math.round(r.duration / 60)),
  };
}

export async function searchPlaces(query) {
  const url =
    "https://nominatim.openstreetmap.org/search?format=json&limit=5&countrycodes=vn&accept-language=vi&q=" +
    encodeURIComponent(query);
  const res = await fetch(url);
  if (!res.ok) throw new Error("Không tìm được địa điểm.");
  return (await res.json()).map((p) => ({
    name: p.display_name.split(",")[0],
    address: p.display_name,
    lat: Number(p.lat),
    lng: Number(p.lon),
  }));
}
