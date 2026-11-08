"""
Tạo/cập nhật tài khoản quản trị, và (tuỳ chọn) nạp vài trạm mẫu.
KHÔNG có mật khẩu mặc định: bắt buộc truyền qua biến môi trường.

    ADMIN_USERNAME=admin ADMIN_PASSWORD='mat-khau-manh' python create_admin.py
    ADMIN_PASSWORD='...' python create_admin.py --seed      # kèm 3 trạm mẫu (chỉ tạo khi chưa có)

Trên Render: tab Shell của service, hoặc chạy qua máy local với DATABASE_URL (External URL).
"""
import os
import sys

import django

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "server_parking.settings")
django.setup()

from django.contrib.auth.models import User  # noqa: E402

from charging_api.models import ChargingStation  # noqa: E402

SAMPLE_STATIONS = [
    {"name": "Trạm Sạc Vincom Ocean Park", "ward": "Đa Tốn, Gia Lâm",
     "address": "Hầm gửi xe Vincom Mega Mall Ocean Park", "latitude": 20.9951, "longitude": 105.9458, "total_slots": 4},
    {"name": "Trạm Sạc Smart City Tây Mỗ", "ward": "Tây Mỗ, Nam Từ Liêm",
     "address": "Bãi đỗ xe thông minh Zone A, Vinhomes Smart City", "latitude": 21.0035, "longitude": 105.7431, "total_slots": 3},
    {"name": "Trạm Sạc Hồ Gươm Central", "ward": "Hàng Trống, Hoàn Kiếm",
     "address": "Bãi đỗ xe ngầm phố Đinh Tiên Hoàng", "latitude": 21.0285, "longitude": 105.8542, "total_slots": 5},
]


def create_admin():
    username = os.environ.get("ADMIN_USERNAME", "admin")
    password = os.environ.get("ADMIN_PASSWORD")
    if not password:
        raise SystemExit("Hãy đặt biến môi trường ADMIN_PASSWORD trước khi chạy.")
    user, created = User.objects.get_or_create(username=username, defaults={"email": os.environ.get("ADMIN_EMAIL", "")})
    user.set_password(password)
    user.is_superuser = user.is_staff = True
    user.save()
    print("Đã tạo admin." if created else "Đã cập nhật mật khẩu admin.")


def seed_stations():
    for data in SAMPLE_STATIONS:
        # ChargingStation.save() tự tạo ô sạc (A1, A2...) và khung giờ 2 tiếng → KHÔNG tạo tay ở đây
        _, created = ChargingStation.objects.get_or_create(name=data["name"], defaults=data)
        print(("Đã tạo: " if created else "Đã có: ") + data["name"])


if __name__ == "__main__":
    create_admin()
    if "--seed" in sys.argv:
        seed_stations()
