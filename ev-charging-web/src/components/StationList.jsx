import { STATUS } from "../config";

export default function StationList({ stations, selectedId, onSelect, loading, waking, error, onRetry }) {
  if (loading)
    return (
      <div className="empty">
        {waking
          ? "Máy chủ đang khởi động (lần đầu có thể mất khoảng 1 phút). Vui lòng đợi…"
          : "Đang tải danh sách trạm…"}
      </div>
    );
  if (error)
    return (
      <div className="empty">
        {error}
        <button className="link" onClick={onRetry}>Thử lại</button>
      </div>
    );
  if (!stations.length) return <div className="empty">Không có trạm nào khớp bộ lọc. Hãy bỏ bớt điều kiện lọc.</div>;

  return (
    <ul className="list">
      {stations.map((s) => (
        <li key={s.id}>
          <button className={s.id === selectedId ? "card selected" : "card"} onClick={() => onSelect(s)}>
            <span className="dot" style={{ background: STATUS[s.status].color }} />
            <span className="card-body">
              <strong>{s.name}</strong>
              <span className="muted">{s.address}</span>
              <span className="meta">
                {s.ward} · {s.freeTimeSlots} khung giờ trống
                {s.distance != null && ` · ${s.distance.toFixed(1)} km`}
              </span>
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}
