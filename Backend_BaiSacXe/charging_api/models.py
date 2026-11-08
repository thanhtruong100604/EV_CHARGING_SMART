from datetime import timedelta

from django.db import models
from django.db.models.signals import post_delete
from django.dispatch import receiver
from django.utils import timezone

SLOT_HOURS = 2  # mỗi lượt sạc kéo dài 2 tiếng
TERMINAL_STATUSES = ("Cancelled", "Completed")


class ChargingStation(models.Model):
    name = models.CharField(max_length=255, verbose_name="Tên trạm")
    ward = models.CharField(max_length=100, verbose_name="Phường/Xã", default="Khác")
    address = models.TextField(verbose_name="Địa chỉ")
    latitude = models.DecimalField(max_digits=20, decimal_places=14, null=True, blank=True)
    longitude = models.DecimalField(max_digits=20, decimal_places=14, null=True, blank=True)
    total_slots = models.IntegerField(default=0)
    available_slots = models.IntegerField(default=0)

    def save(self, *args, **kwargs):
        is_new = self.pk is None
        if is_new:
            self.available_slots = self.total_slots
        super().save(*args, **kwargs)

        # Tự động tạo ô sạc (A1..A10, B1..) và khung giờ 2 tiếng cho trạm mới
        if is_new and self.total_slots > 0:
            new_slots = []
            for i in range(1, self.total_slots + 1):
                row_char = chr(65 + (i - 1) // 10)
                num = (i - 1) % 10 + 1
                new_slots.append(ChargingSlot(station=self, slot_code=f"{row_char}{num}"))
            created_slots = ChargingSlot.objects.bulk_create(new_slots)

            # bulk_create trên một số DB không trả về pk → đọc lại cho chắc
            if any(s.pk is None for s in created_slots):
                created_slots = list(ChargingSlot.objects.filter(station=self))

            time_slots = [
                TimeSlot(station=self, slot=slot, start_hour=hour, is_available=True)
                for slot in created_slots
                for hour in range(0, 24, SLOT_HOURS)
            ]
            TimeSlot.objects.bulk_create(time_slots, ignore_conflicts=True)

    def sync_available_slots(self):
        """Đồng bộ cột available_slots = số ô vật lý đang trống."""
        free = self.slots.filter(is_available=True).count()
        if free != self.available_slots:
            ChargingStation.objects.filter(pk=self.pk).update(available_slots=free)
            self.available_slots = free

    def __str__(self):
        return self.name


class ChargingSlot(models.Model):
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE, related_name="slots")
    slot_code = models.CharField(max_length=5)  # Ví dụ: A1, B2...
    is_available = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.station.name} - {self.slot_code}"


class TimeSlot(models.Model):
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE, related_name="time_slots")
    slot = models.ForeignKey(
        ChargingSlot, on_delete=models.CASCADE, related_name="time_slots", null=True, blank=True
    )
    start_hour = models.IntegerField()
    is_available = models.BooleanField(default=True)

    class Meta:
        unique_together = ("station", "slot", "start_hour")

    def __str__(self):
        return f"{self.start_hour:02d}:00 - {self.end_hour:02d}:00"

    @property
    def end_hour(self):
        return self.start_hour + SLOT_HOURS


class Booking(models.Model):
    STATUS_CHOICES = [
        ("Quick_Booking", "Đang giữ chỗ 10p"),
        ("Confirmed", "Đã giữ chỗ/Đang sạc"),
        ("Completed", "Đã sạc xong"),
        ("Cancelled", "Đã hủy/Hết hạn"),
    ]

    user_id = models.CharField(max_length=100, verbose_name="ID người dùng")
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE)
    booking_time = models.DateTimeField(auto_now_add=True)
    expiry_time = models.DateTimeField(null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default="Quick_Booking")
    qr_code_data = models.TextField(null=True, blank=True)
    amount = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    is_checked_in = models.BooleanField(default=False)
    slot = models.ForeignKey(ChargingSlot, on_delete=models.SET_NULL, null=True, blank=True)
    time_slot = models.ForeignKey(TimeSlot, on_delete=models.SET_NULL, null=True, blank=True)
    scheduled_hour = models.IntegerField(null=True, blank=True, verbose_name="Khung giờ đặt (0-23)")

    def __str__(self):
        return f"{self.user_id} - {self.station.name}"

    def release(self):
        """Trả lại khung giờ / ô sạc mà đơn này đang giữ."""
        if self.time_slot_id:
            TimeSlot.objects.filter(pk=self.time_slot_id).update(is_available=True)
        elif self.slot_id:
            ChargingSlot.objects.filter(pk=self.slot_id).update(is_available=True)
        try:
            self.station.sync_available_slots()
        except ChargingStation.DoesNotExist:
            pass

    def save(self, *args, **kwargs):
        is_new = self.pk is None
        old_status = None
        if not is_new:
            old_status = Booking.objects.filter(pk=self.pk).values_list("status", flat=True).first()

        if is_new:
            if not self.expiry_time:
                self.expiry_time = timezone.now() + timedelta(minutes=10)
            # Đơn tạo từ Admin cũng phải khoá chỗ (đơn tạo từ API đã khoá sẵn trong transaction)
            if self.status not in TERMINAL_STATUSES:
                if self.time_slot_id:
                    TimeSlot.objects.filter(pk=self.time_slot_id).update(is_available=False)
                elif self.slot_id:
                    ChargingSlot.objects.filter(pk=self.slot_id).update(is_available=False)

        super().save(*args, **kwargs)

        # Chỉ trả chỗ MỘT LẦN, đúng lúc đơn chuyển sang Cancelled/Completed
        if (
            not is_new
            and self.status in TERMINAL_STATUSES
            and old_status not in TERMINAL_STATUSES
        ):
            self.release()
        elif is_new:
            try:
                self.station.sync_available_slots()
            except ChargingStation.DoesNotExist:
                pass


@receiver(post_delete, sender=Booking)
def restore_slot_on_delete(sender, instance, **kwargs):
    """Xoá đơn từ Admin: trả lại chỗ nếu đơn chưa kết thúc."""
    if instance.status in TERMINAL_STATUSES:
        return
    try:
        instance.release()
    except Exception:
        # Trạm/ô sạc đã bị xoá theo CASCADE → bỏ qua
        pass
