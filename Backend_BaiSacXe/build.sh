#!/usr/bin/env bash
# Render chạy file này khi deploy (Build Command: bash build.sh). Dừng ngay nếu có lỗi.
set -o errexit

pip install --upgrade pip
pip install -r requirements.txt

python manage.py collectstatic --noinput
python manage.py migrate --noinput
# Đảm bảo mọi trạm đủ ô sạc & khung giờ (không xóa gì, chạy nhiều lần vẫn an toàn)
python manage.py sync_slots
