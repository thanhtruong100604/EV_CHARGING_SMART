from django.core.management.base import BaseCommand

from charging_api.models import SLOT_HOURS, ChargingSlot, ChargingStation, TimeSlot


class Command(BaseCommand):
    help = (
        "Đồng bộ dữ liệu: đảm bảo mỗi trạm đủ số ô sạc (A1, A2...) và mỗi ô có đủ 12 khung giờ 2 tiếng. "
        "An toàn khi chạy nhiều lần, KHÔNG xóa dữ liệu."
    )

    def handle(self, *args, **options):
        new_slots = new_times = 0
        for station in ChargingStation.objects.all():
            existing = list(ChargingSlot.objects.filter(station=station))
            codes = {s.slot_code for s in existing}
            for i in range(1, station.total_slots + 1):
                code = f"{chr(65 + (i - 1) // 10)}{(i - 1) % 10 + 1}"
                if code not in codes:
                    existing.append(ChargingSlot.objects.create(station=station, slot_code=code))
                    new_slots += 1
            for slot in existing:
                for hour in range(0, 24, SLOT_HOURS):
                    _, created = TimeSlot.objects.get_or_create(
                        station=station, slot=slot, start_hour=hour, defaults={"is_available": True}
                    )
                    new_times += created
            station.sync_available_slots()
        self.stdout.write(self.style.SUCCESS(f"Đã tạo thêm {new_slots} ô sạc, {new_times} khung giờ."))
