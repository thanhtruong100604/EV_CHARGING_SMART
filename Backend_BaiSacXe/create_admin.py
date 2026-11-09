"""
Tạo/cập nhật tài khoản quản trị, và (tuỳ chọn) nạp vài trạm mẫu.
KHÔNG có mật khẩu mặc định: bắt buộc truyền qua biến môi trường.

    ADMIN_USERNAME=admin ADMIN_PASSWORD='mat-khau-manh' python create_admin.py
    ADMIN_PASSWORD='...' python create_admin.py --seed      # kèm trạm mẫu (chỉ tạo khi chưa có)

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
    # --- KHU VỰC XÂM TẦM - MỄ SỞ, VĂN GIANG, HƯNG YÊN (5 TRẠM) ---
    {
        "name": "Trạm Sạc EV Xâm Tầm",
        "ward": "Mễ Sở, Văn Giang",
        "address": "Khu vực nhà văn hóa thôn Xâm Tầm, xã Mễ Sở",
        "latitude": 20.9251,
        "longitude": 105.9142,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Mễ Sở Center",
        "ward": "Mễ Sở, Văn Giang",
        "address": "Gần chợ Mễ Sở và tuyến đê chính",
        "latitude": 20.9220,
        "longitude": 105.9110,
        "total_slots": 5,
    },
    {
        "name": "Trạm Sạc Xâm Động Văn Giang",
        "ward": "Mễ Sở, Văn Giang",
        "address": "Khu dân cư thôn Xâm Động, xã Mễ Sở",
        "latitude": 20.9305,
        "longitude": 105.9190,
        "total_slots": 3,
    },
    {
        "name": "Trạm Sạc Thắng Lợi Phía Nam",
        "ward": "Thắng Lợi, Văn Giang",
        "address": "Trục đường trục chính xã Thắng Lợi",
        "latitude": 20.9145,
        "longitude": 105.9330,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Đê Hồng Hà Mễ Sở",
        "ward": "Mễ Sở, Văn Giang",
        "address": "Khu vực chân đê Hồng Hà, xã Mễ Sở",
        "latitude": 20.9340,
        "longitude": 105.9165,
        "total_slots": 6,
    },

    # --- KHU VỰC BẮC TỪ LIÊM, HÀ NỘI (10 TRẠM) ---
    {
        "name": "Trạm Sạc Vincom Plaza Cổ Nhuế",
        "ward": "Cổ Nhuế 1, Bắc Từ Liêm",
        "address": "234 Phạm Văn Đồng, Cổ Nhuế 1",
        "latitude": 21.0532,
        "longitude": 105.7865,
        "total_slots": 6,
    },
    {
        "name": "Trạm Sạc Khu Đô thị Thành phố Giao Lưu",
        "ward": "Cổ Nhuế 1, Bắc Từ Liêm",
        "address": "Đường Phạm Văn Đồng, cạnh chung cư An Bình City",
        "latitude": 21.0498,
        "longitude": 105.7834,
        "total_slots": 8,
    },
    {
        "name": "Trạm Sạc Cổ Nhuế 2",
        "ward": "Cổ Nhuế 2, Bắc Từ Liêm",
        "address": "Khu vực gần Học viện Tài Chính",
        "latitude": 21.0620,
        "longitude": 105.7790,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Đức Diễn Minh Khai",
        "ward": "Minh Khai, Bắc Từ Liêm",
        "address": "Phố Đức Diễn, phường Minh Khai",
        "latitude": 21.0560,
        "longitude": 105.7485,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Nhổn Đại học Công Nghiệp",
        "ward": "Nhổn, Tây Tựu",
        "address": "Khu vực cổng Trường ĐH Công Nghiệp Hà Nội",
        "latitude": 21.0551,
        "longitude": 105.7342,
        "total_slots": 5,
    },
    {
        "name": "Trạm Sạc Phú Diễn",
        "ward": "Phú Diễn, Bắc Từ Liêm",
        "address": "Đường Phú Diễn, gần ga Phú Diễn",
        "latitude": 21.0412,
        "longitude": 105.7601,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Tây Tựu",
        "ward": "Tây Tựu, Bắc Từ Liêm",
        "address": "Khu trung tâm hành chính phường Tây Tựu",
        "latitude": 21.0654,
        "longitude": 105.7250,
        "total_slots": 3,
    },
    {
        "name": "Trạm Sạc Cổ Loa Xuân Đỉnh",
        "ward": "Xuân Đỉnh, Bắc Từ Liêm",
        "address": "Đường Xuân Tảo, khu vực Ngoại Giao Đoàn",
        "latitude": 21.0612,
        "longitude": 105.7955,
        "total_slots": 6,
    },
    {
        "name": "Trạm Sạc Đông Ngạc",
        "ward": "Đông Ngạc, Bắc Từ Liêm",
        "address": "Gần khu vực chân cầu Thăng Long (phía Bắc Từ Liêm)",
        "latitude": 21.0850,
        "longitude": 105.7812,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Thụy Phương",
        "ward": "Thụy Phương, Bắc Từ Liêm",
        "address": "Trục đường chính Thụy Phương",
        "latitude": 21.0811,
        "longitude": 105.7650,
        "total_slots": 3,
    },

    # --- CÁC TRẠM MẪU BAN ĐẦU (VINCOM, SMART CITY, HỒ GƯƠM) ---
    {
        "name": "Trạm Sạc Vincom Ocean Park",
        "ward": "Đa Tốn, Gia Lâm",
        "address": "Hầm gửi xe Vincom Mega Mall Ocean Park",
        "latitude": 20.9951,
        "longitude": 105.9458,
        "total_slots": 4,
    },
    {
        "name": "Trạm Sạc Smart City Tây Mỗ",
        "ward": "Tây Mỗ, Nam Từ Liêm",
        "address": "Bãi đỗ xe thông minh Zone A, Vinhomes Smart City",
        "latitude": 21.0035,
        "longitude": 105.7431,
        "total_slots": 3,
    },
    {
        "name": "Trạm Sạc Hồ Gươm Central",
        "ward": "Hàng Trống, Hoàn Kiếm",
        "address": "Bãi đỗ xe ngầm phố Đinh Tiên Hoàng",
        "latitude": 21.0285,
        "longitude": 105.8542,
        "total_slots": 5,
    },
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
        # ChargingStation.save() tự động tạo ô sạc và khung giờ -> Không cần tạo thủ công
        _, created = ChargingStation.objects.get_or_create(name=data["name"], defaults=data)
        print(("Đã tạo: " if created else "Đã có: ") + data["name"])


if __name__ == "__main__":
    create_admin()
    if "--seed" in sys.argv:
        seed_stations()