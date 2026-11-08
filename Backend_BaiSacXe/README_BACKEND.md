# Backend Django trên Render – cấu hình chuẩn

> **KHÔNG dán mật khẩu database vào code, vào README hay vào chat.** Mật khẩu chỉ nằm trong
> Render → Environment (biến `DATABASE_URL`).

## 1. Chuẩn bị database (Render → Databases → chọn database của bạn)
1. Xem trạng thái: nếu là gói **Free**, database **hết hạn sau 30 ngày** (có thêm 14 ngày ân hạn rồi bị xóa cùng dữ liệu). Hết hạn là server báo lỗi kết nối. Cần thì nâng cấp gói.
2. Mở tab **Info → Connections**, sao chép một trong hai:
   - **Internal Database URL**: dạng `postgresql://user:pass@dpg-xxxx-a/tên_db` (host **không** có đuôi `.render.com`). Nhanh hơn, chỉ dùng được khi web service và database **cùng region** (cả hai Singapore).
   - **External Database URL**: host có đuôi `.singapore-postgres.render.com`. Dùng được từ mọi nơi (kể cả máy bạn).
   Không chắc thì dùng External; Django tự bật SSL.
3. Nếu mật khẩu từng bị lộ: Info → **Credentials → "+ New default credential"** để tạo user mới, cập nhật `DATABASE_URL`, deploy lại, rồi xóa user cũ (biểu tượng thùng rác).

## 2. Web Service (Render → service `carparkingsmart` → Settings)
| Mục | Giá trị |
|---|---|
| Repository / Branch | `thanhtruong-group/ev_charging_smart` / `main` |
| **Root Directory** | `Backend_BaiSacXe` |
| Runtime | Python 3 |
| **Build Command** | `bash build.sh` |
| **Start Command** | `gunicorn server_parking.wsgi:application --bind 0.0.0.0:$PORT --workers 2 --timeout 120` |
| **Health Check Path** | `/health/` |
| Region | cùng region với database (Singapore) |

## 3. Environment (Render → service → Environment → Add Environment Variable)
| Biến | Giá trị | Bắt buộc |
|---|---|:-:|
| `PYTHON_VERSION` | `3.13.5` (Django 6 cần Python ≥ 3.12; service tạo từ lâu có thể đang mặc định bản cũ hơn) | ✅ |
| `DATABASE_URL` | dán URL ở mục 1 | ✅ |
| `SECRET_KEY` | chuỗi ngẫu nhiên ≥ 50 ký tự. Tạo bằng: `python -c "import secrets; print(secrets.token_urlsafe(64))"` | ✅ |
| `PAYMENT_ACCOUNT_NO` | số tài khoản nhận tiền | nên |
| `PAYMENT_BANK_BIN` | `970415` (VietinBank) | nên |
| `PAYMENT_ACCOUNT_NAME` | tên chủ tài khoản, không dấu | nên |
| `BOOKING_AMOUNT` | `100000` | không |
| `REQUIRE_AUTH` | `False` (đổi `True` khi app mới và web đã chạy ổn) | không |
| `CORS_ALLOWED_ORIGINS` | `https://<tên>.gitlab.io` (để trống = cho mọi nơi) | không |
| `PAYMENT_WEBHOOK_SECRET` | bật webhook tự xác nhận thanh toán | không |

Thiếu `SECRET_KEY` hoặc `DATABASE_URL`, server cố ý báo lỗi rõ ràng trong **Logs** (thay vì chạy sai rồi mất dữ liệu).

## 4. Deploy và kiểm tra
1. **Manual Deploy → Clear build cache & deploy latest commit**.
2. Tab **Logs**: build chạy `collectstatic`, `migrate`, `sync_slots`, rồi gunicorn khởi động.
3. Mở `https://carparkingsmart.onrender.com/health/` → phải thấy `{"status": "ok"}`.
4. Mở `https://carparkingsmart.onrender.com/api/stations/` → danh sách trạm (JSON).
Lần đầu sau khi "ngủ" mất khoảng 1 phút để dậy.

## 5. Tạo tài khoản admin (chạy trên máy bạn, ghi thẳng vào database trên Render)
Dùng **External Database URL**. PowerShell:
```powershell
cd Backend_BaiSacXe
$env:DATABASE_URL = "<External Database URL>"
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "<mật khẩu mạnh của bạn>"
python create_admin.py            # thêm --seed để nạp 3 trạm mẫu (chỉ tạo nếu chưa có)
```
Sau đó vào `https://carparkingsmart.onrender.com/admin/`.

## 6. Thanh toán
Hệ thống không tự biết khi tiền vào tài khoản. Hai cách xác nhận:
1. **Thủ công**: Admin → Bookings → chọn đơn → Action **"✅ Xác nhận đã thanh toán"**. App/web đang chờ tự nhận sau vài giây.
2. **Tự động (tuỳ chọn)**: đặt `PAYMENT_WEBHOOK_SECRET`, khai báo ở dịch vụ báo biến động số dư (SePay, Casso…) URL `https://<server>/api/payments/webhook/` với header `Authorization: Apikey <secret>`. Nội dung chuyển khoản chứa `DatCho_<id>` thì đơn được xác nhận. *(Chưa thử với dịch vụ thật; thử bằng giao dịch nhỏ trước.)*

## 7. Chạy thử trên máy
```bash
pip install -r requirements.txt
python manage.py migrate
python manage.py test charging_api      # 30 bài test
python manage.py runserver              # không đặt DATABASE_URL → dùng SQLite
```

## 8. Lỗi thường gặp
| Triệu chứng trong Logs | Nguyên nhân / cách xử lý |
|---|---|
| `No matching distribution found for django>=6` | Python quá cũ → đặt `PYTHON_VERSION=3.13.5` |
| `Chưa đặt biến môi trường SECRET_KEY` / `Chưa đặt DATABASE_URL` | Thêm biến ở mục 3 |
| `could not translate host name "dpg-...-a"` | Dùng Internal URL nhưng khác region → đổi sang External URL |
| `password authentication failed` | Sai/đã đổi mật khẩu → lấy lại URL ở mục 1 |
| `connection refused` / `database ... does not exist` | Database hết hạn hoặc đã xóa (gói Free) |
| `DisallowedHost` | Truy cập bằng tên miền lạ → thêm vào biến `ALLOWED_HOSTS` |
| Trang admin mất giao diện | Build không chạy `./build.sh` (Build Command phải là `bash build.sh`) |
