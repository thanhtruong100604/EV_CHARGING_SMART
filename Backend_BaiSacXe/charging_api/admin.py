from django.contrib import admin

from .models import Booking, ChargingSlot, ChargingStation, TimeSlot


@admin.register(ChargingStation)
class StationAdmin(admin.ModelAdmin):
    list_display = ("name", "ward", "available_slots", "total_slots", "latitude", "longitude")
    search_fields = ("name", "ward")


@admin.action(description="✅ Xác nhận đã thanh toán")
def mark_paid(modeladmin, request, queryset):
    for b in queryset.filter(status="Quick_Booking"):
        b.status = "Confirmed"
        b.save()


@admin.action(description="❌ Hủy đơn và trả chỗ")
def cancel_bookings(modeladmin, request, queryset):
    for b in queryset.exclude(status__in=["Cancelled", "Completed"]):
        b.status = "Cancelled"
        b.save()


@admin.action(description="🏁 Hoàn tất sạc và trả chỗ")
def complete_bookings(modeladmin, request, queryset):
    for b in queryset.filter(status="Confirmed"):
        b.status = "Completed"
        b.save()


@admin.register(Booking)
class BookingAdmin(admin.ModelAdmin):
    list_display = ("id", "user_id", "station", "slot", "scheduled_hour", "status", "booking_time", "expiry_time", "amount")
    list_filter = ("status", "station", "is_checked_in")
    search_fields = ("user_id", "station__name")
    actions = [mark_paid, cancel_bookings, complete_bookings]
    # Đổi trạng thái bằng Actions ở trên (an toàn hơn sửa trực tiếp trong danh sách)


@admin.register(ChargingSlot)
class ChargingSlotAdmin(admin.ModelAdmin):
    list_display = ("slot_code", "station", "is_available")
    list_filter = ("station", "is_available")
    search_fields = ("slot_code",)
    list_editable = ("is_available",)


@admin.register(TimeSlot)
class TimeSlotAdmin(admin.ModelAdmin):
    list_display = ("station", "slot", "start_hour", "is_available")
    list_filter = ("station", "is_available")
    search_fields = ("station__name", "slot__slot_code")
    list_editable = ("is_available",)
