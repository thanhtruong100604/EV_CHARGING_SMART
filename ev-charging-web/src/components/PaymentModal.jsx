import { useEffect, useRef, useState } from "react";
import Modal from "./Modal";
import { api } from "../services/api";
import { useCountdown } from "../hooks/useCountdown";
import { formatClock, formatVnd, hourLabel } from "../utils/format";

/** Giữ chỗ + thanh toán QR. Tự kiểm tra trạng thái mỗi 4 giây (giống app: polling), có đếm ngược theo giờ máy chủ. */
export default function PaymentModal({ bookingId, onClose, onPaid, onEnded }) {
  const [booking, setBooking] = useState(null);
  const [pay, setPay] = useState(null);
  const [error, setError] = useState("");
  const [cancelling, setCancelling] = useState(false);
  const left = useCountdown(booking?.expiry_time);
  const done = useRef(false);
  const cb = useRef({});
  cb.current = { onPaid, onEnded };

  const finish = (b) => {
    if (done.current) return;
    if (b.status === "Confirmed" || b.status === "Completed") {
      done.current = true;
      cb.current.onPaid(b);
    } else if (b.status === "Cancelled") {
      done.current = true;
      cb.current.onEnded(b, "Đơn đặt chỗ đã hết hạn hoặc bị hủy.");
    }
  };

  useEffect(() => {
    let alive = true;
    Promise.all([api.booking(bookingId), api.paymentInfo(bookingId)])
      .then(([b, p]) => {
        if (!alive) return;
        setBooking(b);
        setPay(p);
        finish(b);
      })
      .catch((e) => alive && setError(e.message));
    const timer = setInterval(() => {
      api.booking(bookingId).then((b) => alive && (setBooking(b), finish(b))).catch(() => {});
    }, 4000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bookingId]);

  // Hết giờ đếm ngược → hủy để trả chỗ (server cũng tự hủy)
  useEffect(() => {
    if (left === 0 && booking?.status === "Quick_Booking" && !done.current) {
      api.cancel(bookingId).catch(() => {});
      done.current = true;
      cb.current.onEnded(booking, "Hết thời gian giữ chỗ, đơn đã tự động hủy.");
    }
  }, [left, booking, bookingId]);

  const cancel = async () => {
    setCancelling(true);
    try {
      await api.cancel(bookingId);
      done.current = true;
      onEnded(booking, "Đã hủy đặt chỗ.");
    } catch (e) {
      setError(e.message);
      setCancelling(false);
    }
  };

  const isNow = booking && (booking.scheduled_hour == null || booking.scheduled_hour < 0);

  return (
    <Modal title="Giữ chỗ & thanh toán" onClose={onClose} dismissible={false}>
      {!booking && !error && <p className="muted">Đang tải thông tin đơn…</p>}
      {error && <p className="alert" role="alert">{error}</p>}
      {booking && (
        <>
          <div className={`countdown${left != null && left < 60 ? " urgent" : ""}`}>
            <span>⏳ {isNow ? "Thời gian di chuyển đến trạm" : "Thời gian giữ chỗ còn lại"}</span>
            <strong>{left == null ? "--:--" : formatClock(left)}</strong>
          </div>

          <dl className="kv">
            <dt>Trạm</dt><dd>{booking.station_name}</dd>
            <dt>Ô sạc</dt><dd>{booking.slot_code}</dd>
            <dt>Khung giờ</dt><dd>{isNow ? "Đặt ngay" : hourLabel(booking.scheduled_hour)}</dd>
            <dt>Số tiền</dt><dd><strong>{formatVnd(pay?.amount ?? booking.amount)}</strong></dd>
          </dl>

          {pay && (
            <div className="qr">
              <img src={pay.qr_url} alt="Mã QR chuyển khoản" width="220" height="220" />
              <p className="muted">Quét bằng app ngân hàng / MoMo / ZaloPay</p>
              <dl className="kv small">
                <dt>Số tài khoản</dt><dd>{pay.account_no}</dd>
                <dt>Chủ tài khoản</dt><dd>{pay.account_name}</dd>
                <dt>Nội dung</dt><dd><strong>{pay.content}</strong></dd>
              </dl>
              <p className="hint">Giữ nguyên nội dung chuyển khoản. Hệ thống tự cập nhật khi nhận được tiền; trang này sẽ tự chuyển sang hóa đơn.</p>
            </div>
          )}

          <button className="secondary danger" onClick={cancel} disabled={cancelling}>
            {cancelling ? "Đang hủy…" : "Hủy đặt chỗ"}
          </button>
        </>
      )}
      {!booking && error && <button className="secondary" onClick={onClose}>Đóng</button>}
    </Modal>
  );
}
