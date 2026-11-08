"""Nghiệp vụ đặt chỗ dùng chung cho app Android và web."""
import re
import time
from datetime import timedelta

from django.conf import settings
from django.db import transaction
from django.utils import timezone

from .models import (
    SLOT_HOURS,
    Booking,
    ChargingSlot,
    ChargingStation,
    TimeSlot,
)


class BookingError(Exception):
    """Lỗi nghiệp vụ; status_code dùng để trả HTTP cho client."""

    def __init__(self, message, status_code=400):
        super().__init__(message)
        self.message = message
        self.status_code = status_code


def local_now():
    return timezone.localtime(timezone.now())


def hour_is_past(start_hour, now=None):
    """Khung giờ đã kết thúc (hôm nay)."""
    now = now or local_now()
    return start_hour + SLOT_HOURS <= now.hour


# ---------------------------------------------------------------- dọn đơn
_last_sweep = 0.0
SWEEP_INTERVAL = 15  # giây


def release_expired_and_finished(force=False):
    """
    - Đơn giữ chỗ quá hạn (Quick_Booking) → Cancelled, trả chỗ.
    - Đơn đã thanh toán (Confirmed) có khung giờ đã qua → Completed, trả chỗ.
    Gọi trước các API đọc dữ liệu. Có giới hạn tần suất để không tốn DB.
    """
    global _last_sweep
    mono = time.monotonic()
    if not force and mono - _last_sweep < SWEEP_INTERVAL:
        return
    _last_sweep = mono

    now = timezone.now()
    for b in Booking.objects.filter(status="Quick_Booking", expiry_time__lt=now).select_related("station"):
        b.status = "Cancelled"
        b.save()

    local = timezone.localtime(now)
    max_quick = timedelta(hours=settings.QUICK_CONFIRMED_MAX_HOURS)
    for b in Booking.objects.filter(status="Confirmed").select_related("station"):
        done = False
        if b.scheduled_hour is not None and b.scheduled_hour >= 0:
            booked_day = timezone.localtime(b.booking_time).date()
            done = booked_day < local.date() or local.hour >= b.scheduled_hour + SLOT_HOURS
        else:  # đặt ngay, không có khung giờ
            done = now - b.booking_time > max_quick
        if done:
            b.status = "Completed"
            b.save()


# --------------------------------------------------------------- tạo đơn
def _clamp_hold(minutes):
    try:
        minutes = int(minutes)
    except (TypeError, ValueError):
        return settings.BOOKING_HOLD_MINUTES
    return max(5, min(60, minutes))


@transaction.atomic
def create_booking(user_id, station_id, slot_id, scheduled_hour=None, hold_minutes=None):
    """
    Tạo đơn giữ chỗ. scheduled_hour None/âm = "đặt ngay" (giữ cả ô).
    Dùng select_for_update để 2 người không giữ trùng một chỗ.
    """
    user_id = (user_id or "").strip()
    if not user_id:
        raise BookingError("Thiếu thông tin người dùng")

    try:
        station = ChargingStation.objects.get(pk=int(station_id))
        slot = ChargingSlot.objects.select_for_update().get(pk=int(slot_id), station=station)
    except (TypeError, ValueError):
        raise BookingError("Dữ liệu trạm/ô sạc không hợp lệ")
    except ChargingStation.DoesNotExist:
        raise BookingError("Không tìm thấy trạm sạc", 404)
    except ChargingSlot.DoesNotExist:
        raise BookingError("Không tìm thấy ô sạc trong trạm này", 404)

    pending = Booking.objects.filter(user_id=user_id, status="Quick_Booking").count()
    if pending >= settings.MAX_PENDING_HOLDS:
        raise BookingError("Bạn đang giữ quá nhiều chỗ chưa thanh toán. Hãy thanh toán hoặc hủy bớt.", 400)

    try:
        hour = None if scheduled_hour in (None, "") else int(scheduled_hour)
    except (TypeError, ValueError):
        raise BookingError("Khung giờ không hợp lệ")

    if hour is None or hour < 0:  # ---- đặt ngay
        if not slot.is_available:
            raise BookingError("Ô sạc này đang có người giữ hoặc đang sạc", 409)
        slot.is_available = False
        slot.save(update_fields=["is_available"])
        booking = Booking(
            user_id=user_id,
            station=station,
            slot=slot,
            status="Quick_Booking",
            amount=settings.BOOKING_AMOUNT,
            expiry_time=timezone.now() + timedelta(minutes=_clamp_hold(hold_minutes)),
        )
    else:  # ---- đặt theo khung giờ
        if hour > 23 or hour % SLOT_HOURS != 0:
            raise BookingError("Khung giờ phải là giờ chẵn từ 0 đến 22")
        if hour_is_past(hour):
            raise BookingError("Khung giờ này đã qua, hãy chọn khung giờ khác")
        try:
            time_slot = TimeSlot.objects.select_for_update().get(
                station=station, slot=slot, start_hour=hour
            )
        except TimeSlot.DoesNotExist:
            raise BookingError("Không tìm thấy khung giờ", 404)
        if not time_slot.is_available:
            raise BookingError("Khung giờ này đã được người khác đặt", 409)
        time_slot.is_available = False
        time_slot.save(update_fields=["is_available"])
        booking = Booking(
            user_id=user_id,
            station=station,
            slot=slot,
            time_slot=time_slot,
            scheduled_hour=hour,
            status="Quick_Booking",
            amount=settings.BOOKING_AMOUNT,
            expiry_time=timezone.now() + timedelta(minutes=settings.BOOKING_HOLD_MINUTES),
        )

    booking.save()
    return booking


# ------------------------------------------------------------ thanh toán
def payment_content(booking_id):
    return f"DatCho_{booking_id}"


def payment_info(booking):
    amount = int(booking.amount or settings.BOOKING_AMOUNT)
    content = payment_content(booking.id)
    bin_ = settings.PAYMENT_BANK_BIN
    acc = settings.PAYMENT_ACCOUNT_NO
    from urllib.parse import quote

    qr = (
        f"https://img.vietqr.io/image/{bin_}-{acc}-compact2.png"
        f"?amount={amount}&addInfo={quote(content)}"
        f"&accountName={quote(settings.PAYMENT_ACCOUNT_NAME)}"
    )
    return {
        "booking_id": booking.id,
        "amount": amount,
        "content": content,
        "bank_bin": bin_,
        "account_no": acc,
        "account_name": settings.PAYMENT_ACCOUNT_NAME,
        "qr_url": qr,
        "expires_at": booking.expiry_time,
    }


_CONTENT_RE = re.compile(r"DAT\s*CHO[\s_\-]*(\d+)", re.IGNORECASE)


def confirm_from_transfer(content, amount):
    """Webhook ngân hàng: nội dung chuyển khoản chứa 'DatCho_<id>' và đủ tiền → Confirmed."""
    m = _CONTENT_RE.search(content or "")
    if not m:
        return None, "Không tìm thấy mã đặt chỗ trong nội dung"
    try:
        booking = Booking.objects.select_related("station").get(pk=int(m.group(1)))
    except Booking.DoesNotExist:
        return None, "Không có đơn tương ứng"
    if booking.status == "Confirmed":
        return booking, "Đơn đã được xác nhận trước đó"
    if booking.status != "Quick_Booking":
        return None, f"Đơn đang ở trạng thái {booking.status}, không thể xác nhận"
    if int(amount or 0) < int(booking.amount or settings.BOOKING_AMOUNT):
        return None, "Số tiền chuyển chưa đủ"
    booking.status = "Confirmed"
    # Đã nhận tiền thì giữ chỗ, kể cả khi vừa quá hạn vài giây
    booking.save()
    return booking, "Đã xác nhận thanh toán"
