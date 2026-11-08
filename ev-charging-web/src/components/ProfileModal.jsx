import { useEffect, useState } from "react";
import Modal from "./Modal";
import { api } from "../services/api";

export default function ProfileModal({ user, onClose, onHistory, onLogout }) {
  const [stats, setStats] = useState(null);
  useEffect(() => {
    api.stats(user.username).then(setStats).catch(() => setStats({ total: "—", completed: "—", cancelled: "—" }));
  }, [user.username]);

  return (
    <Modal title="👤 Hồ sơ" onClose={onClose}>
      <p><strong>{user.username}</strong></p>
      {user.email && <p className="muted">{user.email}</p>}
      <div className="stats">
        <div><strong>{stats?.total ?? "…"}</strong><span>Tổng đơn</span></div>
        <div><strong>{stats?.completed ?? "…"}</strong><span>Thành công</span></div>
        <div><strong>{stats?.cancelled ?? "…"}</strong><span>Đã hủy</span></div>
      </div>
      <div className="stack">
        <button className="secondary" onClick={onHistory}>📋 Lịch sử đặt chỗ</button>
        <button className="secondary danger" onClick={onLogout}>Đăng xuất</button>
      </div>
    </Modal>
  );
}
