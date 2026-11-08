# Bản cập nhật v2 – cấu hình Render + sửa giao diện điện thoại

Gói này là **repo đầy đủ** (có cả app Android). Cách dùng: giải nén ra thư mục mới, rồi chép toàn bộ nội dung vào thư mục repo `NCKH_DongBo_AppWeb` của bạn (chọn Replace/ghi đè), giữ nguyên thư mục `.git`.

## Việc cần làm (theo thứ tự)
1. Giải nén, chép đè. **Xóa file cũ không còn dùng**: `Backend_BaiSacXe/charging_api/__pycache__`, `Backend_BaiSacXe/server_parking/__pycache__` (đã bị commit nhầm).
2. Trong thư mục repo chạy:
   ```
   git rm -r --cached --ignore-unmatch .venv ev-charging-web/node_modules Backend_BaiSacXe/charging_api/__pycache__ Backend_BaiSacXe/server_parking/__pycache__
   git add -A
   git commit -m "Cau hinh Render, backend day du, sua giao dien mobile"
   git push
   ```
3. Làm theo **Backend_BaiSacXe/README_BACKEND.md** (mục 1 → 4) trên Render Dashboard.

## Có gì mới
- **Backend đầy đủ** (bản trước thiếu `manage.py`, `wsgi.py`, `migrations/` nên Render không chạy được).
- `settings.py`: bỏ hẳn mật khẩu/khóa viết cứng; thiếu biến môi trường thì báo lỗi rõ; health check `/health/` không bị chuyển hướng HTTPS; log ra console để xem trên Render; trang admin không lỗi dù quên `collectstatic`.
- `build.sh`: cài thư viện → `collectstatic` → `migrate` → `sync_slots`.
- `sync_slots` (mới): tự bù ô sạc & khung giờ còn thiếu cho mọi trạm; chạy nhiều lần vẫn an toàn, không xóa dữ liệu.
- `create_admin.py`: không còn tài khoản/mật khẩu mặc định (`admin/admin123`…). Trước đây script tự tạo thêm ô sạc trong khi `ChargingStation.save()` đã tự tạo → mỗi trạm bị **nhân đôi ô sạc** và ô thừa không có khung giờ. Đã sửa. Tọa độ trạm Smart City (kinh độ 104.74 nằm ở Hòa Bình) đã sửa thành 105.74.
- Đã **xóa** `create_time_slots.py` ở gốc backend: script cũ này xóa TẤT CẢ khung giờ rồi tạo lại bằng trường `date` đã bị bỏ → sẽ làm hỏng dữ liệu nếu chạy. Dùng `python manage.py sync_slots` thay thế.
- Web: chỉnh giao diện điện thoại (chiều cao theo thanh địa chỉ, chữ ô nhập ≥16px để iOS không tự phóng to, vùng bấm lớn hơn, bộ lọc không chiếm hết màn hình, hộp thoại dạng tấm trượt từ dưới).
- `render.yaml` (tuỳ chọn), `.gitignore`, `.python-version`.
