import { useEffect, useState } from "react";
import { SLOT_HOURS } from "../config";
import { api } from "../services/api";
import { useToast } from "../context/ToastContext";
import { distanceKm, estimateHoldMinutes, hourLabel } from "../utils/format";

const HOURS = Array.from({ length: 12 }, (_, i) => i * SLOT_HOURS);

/** Chi tiết trạm + chọn ô + chọn khung giờ + đặt chỗ (giống luồng đặt chỗ trong app). */
export default function StationSheet({
  station, userPosition, onLocate, onClose, onBooked, onRoute, routeInfo, routing,
}) {
  const toast = useToast();
  const [mode, setMode] = useState("hour"); // "hour" = theo khung giờ, "now" = đặt ngay
  const [slots, setSlots] = useState(null);
  const [slot, setSlot] = useState(null);
  const [blocked, setBlocked] = useState(null); // giờ không đặt được của ô đã chọn
  const [hour, setHour] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const loadSlots = () => {
    setSlots(null);
    setError("");
    api.slots(station.id).then(setSlots).catch((e) => setError(e.message));
  };

  useEffect(() => {
    setSlot(null);
    setHour(null);
    setBlocked(null);
    loadSlots();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [station.id]);

  useEffect(() => {
    setHour(null);
    setBlocked(null);
    if (!slot || mode !== "hour") return undefined;
    let alive = true;
    api.bookedHours(station.id, slot.id)
      .then((h) => alive && setBlocked(new Set(h)))
      .catch((e) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [slot, mode, station.id]);

  const changeMode = (m) => {
    setMode(m);
    setSlot(null);
    setError("");
  };

  const submit = async () => {
    setBusy(true);
    setError("");
    try {
      let holdMinutes;
      if (mode === "now") {
        const pos = userPosition || (await onLocate());
        holdMinutes = estimateHoldMinutes(pos ? distanceKm(pos.lat, pos.lng, station.lat, station.lng) : null);
        if (!pos) toast("Chưa có vị trí của bạn nên hệ thống giữ chỗ mặc định 15 phút.", "warn");
      }
      const booking = await api.createBooking({
        stationId: station.id,
        slotId: slot.id,
        hour: mode === "hour" ? hour : null,
        holdMinutes,
      });
      onBooked(booking);
    } catch (e) {
      setError(e.message);
      // Có người vừa đặt trước → tải lại trạng thái để chọn lại
      if (e.status === 409) {
        loadSlots();
        setSlot(null);
      }
    } finally {
      setBusy(false);
    }
  };

  const canSubmit = slot && (mode === "now" || hour != null) && !busy;

  return (
    <aside className="sheet" aria-label={`Chi tiết ${station.name}`}>
      <div className="sheet-head">
        <div>
          <h2>{station.name}</h2>
          <p className="muted">{station.address}</p>
          <p className="meta">{station.ward} · {station.freeTimeSlots} khung giờ trống · mỗi lượt sạc 2 giờ</p>
        </div>
        <button className="icon-btn" onClick={onClose} aria-label="Đóng">✕</button>
      </div>

      <div className="sheet-row">
        <button className="secondary" onClick={onRoute} disabled={routing}>
          {routing ? "Đang tìm đường…" : "🧭 Chỉ đường"}
        </button>
        {routeInfo && (
          <span className="route-info">{routeInfo.distanceKm.toFixed(1)} km · ~{routeInfo.minutes} phút</span>
        )}
      </div>

      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={mode === "hour"} className={mode === "hour" ? "tab active" : "tab"} onClick={() => changeMode("hour")}>
          Đặt theo giờ
        </button>
        <button role="tab" aria-selected={mode === "now"} className={mode === "now" ? "tab active" : "tab"} onClick={() => changeMode("now")}>
          Đặt ngay
        </button>
      </div>
      <p className="hint">
        {mode === "hour"
          ? "Chọn ô sạc, rồi chọn khung giờ 2 tiếng. Thanh toán trong 10 phút để giữ chỗ."
          : "Giữ ô sạc ngay bây giờ. Bạn có thời gian di chuyển đến trạm (ước tính theo vị trí của bạn)."}
      </p>

      <h3>1. Chọn ô sạc</h3>
      {!slots && !error && <p className="muted">Đang tải danh sách ô…</p>}
      {slots && !slots.length && <p className="muted">Trạm này chưa có ô sạc.</p>}
      {slots && (
        <div className="grid slots">
          {slots.map((s) => {
            const disabled = mode === "now" && !s.is_available;
            return (
              <button
                key={s.id}
                className={`cell${slot?.id === s.id ? " on" : ""}${disabled ? " off" : ""}`}
                disabled={disabled}
                onClick={() => setSlot(s)}
                title={disabled ? "Ô đang có người giữ" : s.slot_code}
              >
                {s.slot_code}
              </button>
            );
          })}
        </div>
      )}

      {mode === "hour" && slot && (
        <>
          <h3>2. Chọn khung giờ — ô {slot.slot_code}</h3>
          {!blocked && !error && <p className="muted">Đang kiểm tra giờ trống…</p>}
          {blocked && (
            <div className="grid hours">
              {HOURS.map((h) => (
                <button
                  key={h}
                  className={`cell${hour === h ? " on" : ""}${blocked.has(h) ? " off" : ""}`}
                  disabled={blocked.has(h)}
                  onClick={() => setHour(h)}
                  title={blocked.has(h) ? "Đã có người đặt hoặc đã qua giờ" : hourLabel(h)}
                >
                  {hourLabel(h)}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      {error && <p className="alert" role="alert">{error}</p>}

      <button className="primary" disabled={!canSubmit} onClick={submit}>
        {busy
          ? "Đang giữ chỗ…"
          : mode === "hour"
            ? slot && hour != null ? `Đặt ô ${slot.slot_code} · ${hourLabel(hour)}` : "Chọn ô và khung giờ"
            : slot ? `Giữ ô ${slot.slot_code} ngay` : "Chọn ô sạc"}
      </button>
    </aside>
  );
}
