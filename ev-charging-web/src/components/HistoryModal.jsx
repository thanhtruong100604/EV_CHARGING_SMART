import { useEffect, useState } from "react";
import Modal from "./Modal";
import { BOOKING_STATUS } from "../config";
import { api } from "../services/api";
import { useToast } from "../context/ToastContext";
import { formatDateTime, hourLabel } from "../utils/format";

export default function HistoryModal({ username, onClose, onPay, onInvoice }) {
  const toast = useToast();
  const [items, setItems] = useState(null);
  const [error, setError] = useState("");

  const load = () => {
    setError("");
    api.history(username).then(setItems).catch((e) => setError(e.message));
  };
  useEffect(load, [username]);

  const cancel = async (id) => {
    try {
      await api.cancel(id);
      toast("Đã hủy đặt chỗ.", "ok");
      load();
    } catch (e) {
      toast(e.message, "bad");
    }
  };

  return (
    <Modal title="📋 Lịch sử đặt chỗ" onClose={onClose} wide>
      {!items && !error && <p className="muted">Đang tải lịch sử…</p>}
      {error && (
        <p className="alert" role="alert">
          {error} <button className="link" onClick={load}>Thử lại</button>
        </p>
      )}
      {items && !items.length && <p className="muted">Bạn chưa có lần đặt chỗ nào.</p>}
      <ul className="history">
        {items?.map((b) => {
          const st = BOOKING_STATUS[b.status] || { label: b.status, icon: "•", tone: "info" };
          const pending = b.status === "Quick_Booking" && new Date(b.expiry_time) > new Date();
          const paid = b.status === "Confirmed" || b.status === "Completed";
          return (
            <li key={b.id} className="history-item">
              <div>
                <strong>{st.icon} {b.station_name || "Trạm sạc"}</strong>
                <span className={`badge ${st.tone}`}>{st.label}</span>
                <p className="muted">
                  Ô {b.slot_code || "—"} · {b.time_label || "Đặt ngay"} · {formatDateTime(b.booking_time)}
                </p>
              </div>
              <div className="row">
                {pending && <button className="primary sm" onClick={() => onPay(b.id)}>Thanh toán</button>}
                {pending && <button className="secondary sm" onClick={() => cancel(b.id)}>Hủy</button>}
                {paid && <button className="secondary sm" onClick={() => onInvoice(b)}>Hóa đơn</button>}
              </div>
            </li>
          );
        })}
      </ul>
    </Modal>
  );
}
