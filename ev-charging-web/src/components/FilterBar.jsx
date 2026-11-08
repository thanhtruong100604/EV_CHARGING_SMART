import { useRef, useState } from "react";
import { STATUS } from "../config";
import { searchPlaces } from "../services/geo";
import { useToast } from "../context/ToastContext";

export default function FilterBar({
  query, onQuery, status, onStatus, ward, onWard, wards,
  onLocate, locating, onPickPlace,
}) {
  const toast = useToast();
  const [places, setPlaces] = useState([]);
  const [searching, setSearching] = useState(false);
  const [listening, setListening] = useState(false);
  const recRef = useRef(null);

  const findPlace = async () => {
    if (!query.trim()) return;
    setSearching(true);
    try {
      const found = await searchPlaces(query.trim());
      setPlaces(found);
      if (!found.length) toast("Không tìm thấy địa điểm phù hợp.", "warn");
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setSearching(false);
    }
  };

  const voice = () => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return toast("Trình duyệt này chưa hỗ trợ tìm bằng giọng nói (hãy dùng Chrome).", "warn");
    if (listening) return recRef.current?.stop();
    const rec = new SR();
    rec.lang = "vi-VN";
    rec.onstart = () => setListening(true);
    rec.onend = () => setListening(false);
    rec.onerror = () => toast("Không nhận được giọng nói.", "warn");
    rec.onresult = (e) => onQuery(e.results[0][0].transcript);
    recRef.current = rec;
    rec.start();
  };

  return (
    <div className="filters">
      <div className="search-row">
        <input
          type="search"
          value={query}
          onChange={(e) => {
            onQuery(e.target.value);
            setPlaces([]);
          }}
          onKeyDown={(e) => e.key === "Enter" && findPlace()}
          placeholder="Tìm trạm hoặc địa điểm…"
          aria-label="Tìm trạm sạc hoặc địa điểm"
        />
        <button className={listening ? "icon-btn rec" : "icon-btn"} onClick={voice} aria-label="Tìm bằng giọng nói" title="Giọng nói">🎤</button>
        <button className="icon-btn" onClick={findPlace} disabled={searching} aria-label="Tìm địa điểm trên bản đồ" title="Tìm địa điểm (Enter)">🔎</button>
      </div>

      {places.length > 0 && (
        <ul className="places">
          {places.map((p) => (
            <li key={`${p.lat},${p.lng}`}>
              <button
                onClick={() => {
                  onPickPlace(p);
                  setPlaces([]);
                }}
              >
                <strong>{p.name}</strong>
                <span className="muted">{p.address}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="chips" role="group" aria-label="Lọc theo phường/xã">
        <button className={ward === "" ? "chip active" : "chip"} onClick={() => onWard("")}>Tất cả</button>
        {wards.map((w) => (
          <button key={w} className={ward === w ? "chip active" : "chip"} onClick={() => onWard(w)}>{w}</button>
        ))}
      </div>

      <div className="chips" role="group" aria-label="Lọc theo tình trạng">
        <button className={status === "" ? "chip active" : "chip"} onClick={() => onStatus("")}>Mọi tình trạng</button>
        {Object.entries(STATUS).map(([key, s]) => (
          <button key={key} className={status === key ? "chip active" : "chip"} onClick={() => onStatus(key)}>
            <i style={{ background: s.color }} /> {s.label}
          </button>
        ))}
      </div>

      <button className="primary" onClick={onLocate} disabled={locating}>
        {locating ? "Đang định vị…" : "📍 Tìm trạm gần tôi"}
      </button>
    </div>
  );
}
