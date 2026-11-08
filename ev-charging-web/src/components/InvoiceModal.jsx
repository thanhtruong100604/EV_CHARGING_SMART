import Modal from "./Modal";
import { formatDateTime, formatVnd, hourLabel } from "../utils/format";

export default function InvoiceModal({ booking, onClose }) {
  const isNow = booking.scheduled_hour == null || booking.scheduled_hour < 0;
  return (
    <Modal title="Hóa đơn" onClose={onClose}>
      <div className="invoice" id="invoice">
        <h3>⚡ EV Charging Smart</h3>
        <p className="muted">Hóa đơn đặt chỗ sạc</p>
        <dl className="kv">
          <dt>Mã HĐ</dt><dd>#{booking.id}</dd>
          <dt>Trạm</dt><dd>{booking.station_name}</dd>
          <dt>Ô sạc</dt><dd>{booking.slot_code}</dd>
          <dt>Khung giờ</dt><dd>{isNow ? "Đặt ngay" : hourLabel(booking.scheduled_hour)}</dd>
          <dt>Thời gian đặt</dt><dd>{formatDateTime(booking.booking_time)}</dd>
          <dt>Số tiền</dt><dd><strong>{formatVnd(booking.amount)}</strong></dd>
        </dl>
        <p className="paid">✅ ĐÃ THANH TOÁN</p>
      </div>
      <div className="row">
        <button className="primary" onClick={() => window.print()}>🖨️ In / Lưu PDF</button>
        <button className="secondary" onClick={onClose}>Đóng</button>
      </div>
    </Modal>
  );
}
