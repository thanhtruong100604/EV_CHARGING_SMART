from django.core.management.base import BaseCommand
from charging_api.models import ChargingStation, ChargingSlot, TimeSlot

class Command(BaseCommand):
    help = 'Tạo TimeSlot cho tất cả trạm và ô sạc hiện có'

    def handle(self, *args, **options):
        created_count = 0
        for station in ChargingStation.objects.all():
            for slot in ChargingSlot.objects.filter(station=station):
                for hour in range(0, 23, 2):
                    obj, created = TimeSlot.objects.get_or_create(
                        station=station,
                        slot=slot,
                        start_hour=hour,
                        defaults={'is_available': True}
                    )
                    if created:
                        created_count += 1
        
        self.stdout.write(f'Đã tạo {created_count} TimeSlot')