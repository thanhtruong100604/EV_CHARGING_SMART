from django.contrib.auth.models import User
from rest_framework import serializers

from .models import Booking, ChargingSlot, ChargingStation


class UserSerializer(serializers.ModelSerializer):
    email = serializers.EmailField(required=False, allow_blank=True)

    class Meta:
        model = User
        fields = ["id", "username", "password", "email"]
        extra_kwargs = {"password": {"write_only": True, "min_length": 6}}

    def create(self, validated_data):
        return User.objects.create_user(**validated_data)


class ChargingStationSerializer(serializers.ModelSerializer):
    # Hai trường này được tính sẵn trong queryset (xem views.station_queryset)
    available_slots = serializers.SerializerMethodField()
    real_available_time_slots = serializers.SerializerMethodField()

    class Meta:
        model = ChargingStation
        fields = [
            "id", "name", "ward", "address", "latitude", "longitude",
            "total_slots", "available_slots", "real_available_time_slots",
        ]

    def get_available_slots(self, obj):
        return getattr(obj, "free_slots", obj.available_slots)

    def get_real_available_time_slots(self, obj):
        return getattr(obj, "free_time_slots", 0)

    def to_representation(self, instance):
        data = super().to_representation(instance)
        # Android parse latitude/longitude là Double → trả số, không trả chuỗi Decimal
        for key in ("latitude", "longitude"):
            if data.get(key) is not None:
                data[key] = float(data[key])
        return data


class ChargingSlotSerializer(serializers.ModelSerializer):
    class Meta:
        model = ChargingSlot
        fields = ["id", "slot_code", "is_available"]  # khớp với data class ChargingSlot trên Android


class BookingSerializer(serializers.ModelSerializer):
    station_name = serializers.CharField(source="station.name", read_only=True)
    station_address = serializers.CharField(source="station.address", read_only=True)
    slot_code = serializers.SerializerMethodField()
    status_label = serializers.CharField(source="get_status_display", read_only=True)
    time_label = serializers.SerializerMethodField()

    class Meta:
        model = Booking
        fields = [
            "id", "user_id", "station", "station_name", "station_address",
            "slot", "slot_code", "time_slot", "scheduled_hour", "time_label",
            "status", "status_label", "booking_time", "expiry_time",
            "amount", "is_checked_in", "qr_code_data",
        ]
        read_only_fields = fields

    def get_slot_code(self, obj):
        return obj.slot.slot_code if obj.slot else None

    def get_time_label(self, obj):
        h = obj.scheduled_hour
        if h is None or h < 0:
            return None
        return f"{h:02d}:00 – {h + 2:02d}:00"
