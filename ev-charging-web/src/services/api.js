import { http } from "./http";

// Danh sách endpoint — dùng CHUNG với app Android (cùng Django backend)
export const api = {
  login: (username, password) => http.post("/api-token-auth/", { username, password }),
  register: ({ username, email, password }) => http.post("/register/", { username, email, password }),

  stations: () => http.get("/stations/").then((list) => list.map(normalizeStation).filter(Boolean)),
  slots: (stationId) => http.get(`/stations/${stationId}/slots/`),
  bookedHours: (stationId, slotId) =>
    http.get("/bookings/booked_hours/", { station_id: stationId, slot_id: slotId }),

  // hour = null → "Đặt ngay" (giữ cả ô, holdMinutes = thời gian di chuyển dự kiến)
  createBooking: ({ stationId, slotId, hour = null, holdMinutes }) =>
    http.post("/bookings/", {
      station: stationId,
      slot: slotId,
      scheduled_hour: hour ?? -1,
      hold_minutes: holdMinutes,
      status: "Quick_Booking",
    }),
  booking: (id) => http.get(`/bookings/${id}/`),
  paymentInfo: (id) => http.get(`/bookings/${id}/payment_info/`),
  cancel: (id) => http.post(`/bookings/${id}/cancel/`),
  history: (username) => http.get("/bookings/", { user_id: username }),
  stats: (username) => http.get("/bookings/stats/", { user_id: username }),
};

export function normalizeStation(s) {
  const lat = Number(s.latitude);
  const lng = Number(s.longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  const freeTimeSlots = Number(s.real_available_time_slots ?? 0);
  return {
    id: s.id,
    name: s.name,
    address: s.address || "",
    ward: s.ward || "Khác",
    lat,
    lng,
    totalSlots: Number(s.total_slots ?? 0),
    freeSlots: Number(s.available_slots ?? 0),
    freeTimeSlots,
    status: freeTimeSlots > 0 ? "free" : "busy",
  };
}
