# Đồng bộ App ↔ Web ↔ Django

Đây là **repo đầy đủ**, gồm cả 3 phần (không còn là gói "chép đè"):

```
CarParkingSmart-main/   ← App Android ĐẦY ĐỦ (Gradle, manifest, layout, 29 file Kotlin; 5 file đã sửa)
Backend_BaiSacXe/       ← Django đầy đủ (manage.py, migrations, build.sh, README_BACKEND.md)
ev-charging-web/        ← Web React đầy đủ
render.yaml, .gitignore, .python-version, .gitlab-ci.yml
```
App Android: 5 file đã sửa là `MainActivity.kt`, `api/ApiService.kt`, `api/RetrofitClient.kt`, `auth/AuthViewModel.kt`, `auth/LoginActivity.kt`.

## Thứ tự làm
1. **Backend trước** (app và web cùng phụ thuộc): làm theo `Backend_BaiSacXe/README_BACKEND.md` (đặt biến môi trường trên Render → deploy → kiểm tra `/health/`).
2. **Web**: `cd ev-charging-web && npm install && npm run dev`. Mặc định gọi `https://carparkingsmart.onrender.com/api` (đổi trong `.env`). Push lên GitLab → Pages tự build.
3. **App**: mở Android Studio, Sync Gradle, **build và chạy thử** (xem "Chưa kiểm chứng").
4. Cuối cùng đặt `REQUIRE_AUTH=True` trên Render.

## Bảng chức năng

| Chức năng | App | Web | Ghi chú |
|---|:-:|:-:|---|
| Đăng ký / đăng nhập (token) | ✅ | ✅ | Cùng tài khoản dùng được cả hai nơi |
| Bản đồ + danh sách trạm, số khung giờ trống | ✅ | ✅ | |
| Lọc theo phường/xã, tìm trạm gần nhất | ✅ | ✅ | |
| Tìm địa điểm, tìm bằng giọng nói | ✅ | ✅ | Web: gõ rồi Enter / 🔎; giọng nói cần Chrome |
| Chỉ đường | ✅ từng bước + giọng nói | ⚠️ vẽ tuyến + km/phút | Web chưa có điều hướng từng bước |
| Chọn ô → chọn khung giờ 2 tiếng → đặt | ✅ | ✅ | Giờ đã qua / đã có người đặt bị khóa |
| "Đặt ngay" (giữ ô, thời gian đến theo khoảng cách) | ✅ | ✅ | App nay gửi `hold_minutes` nên server giữ đủ thời gian |
| Thanh toán QR + đếm ngược 10 phút + tự kiểm tra | ✅ | ✅ | Cùng nguồn số tiền/tài khoản từ server |
| Hóa đơn | ✅ ảnh | ✅ in/PDF | |
| Lịch sử đặt chỗ, thống kê hồ sơ | ✅ 10 gần nhất | ✅ đầy đủ + hủy | |
| Chế độ sáng/tối | ✅ | ✅ | |
| Mở lại đơn đang chờ thanh toán sau khi tải lại | ❌ | ✅ | Có thể thêm cho app sau |
| Lưu vị trí xe / tìm xe của tôi | ✅ | ❌ | Chưa làm cho web |
| Trạm yêu thích | ❌ | ❌ | App ghi "đang phát triển" |

## Lỗi đã sửa (nguyên nhân app "không ổn định")
**App**
- Timeout mặc định OkHttp chỉ 10 giây, trong khi Render gói miễn phí "ngủ" và cần 30–60 giây để dậy → request đầu hay lỗi. Nay timeout 30/60 giây, tự thử lại request đọc, báo "máy chủ đang khởi động".
- Tải danh sách trạm lỗi thì im lặng → nay báo và tự thử lại 3 lần.
- Đăng nhập thất bại luôn hiện một câu chung → nay nói rõ sai mật khẩu hay lỗi mạng; nút khóa khi đang xử lý.
- App gọi `update_status/` mà server **không có** → hết giờ thanh toán không hủy được đơn trên server. Nay server có endpoint này.
- Polling thanh toán và đồng hồ đếm ngược không dừng khi thoát màn hình → nay dọn trong `onDestroy`.
- Mọi request nay gửi token; token hết hạn tự về màn đăng nhập.
- Thông báo lỗi đặt chỗ lấy từ server thay vì đoán theo mã lỗi.

**Server**
- `/api/stations/{id}/cancel/` nằm nhầm viewset → chuyển về `bookings`, có kiểm tra chủ đơn.
- **Ai cũng xóa/sửa được trạm sạc**, và ai cũng tự xác nhận thanh toán không cần trả tiền → nay chỉ nhân viên.
- Danh sách đơn không lọc user thì trả đơn của tất cả mọi người → nay trả rỗng.
- Hủy đơn "Đặt ngay" không trả lại ô sạc; hủy 2 lần có thể giải phóng nhầm chỗ người khác → sửa.
- Hai người đặt cùng lúc có thể trùng chỗ → nay khóa giao dịch (`select_for_update`).
- Khung giờ đã đặt bị khóa **mãi mãi** (không có ngày) → nay tự trả khi hết khung giờ; không cho đặt giờ đã qua; múi giờ đổi sang `Asia/Ho_Chi_Minh`.
- Mở danh sách trạm chạy N+1 truy vấn → còn 1 truy vấn.
- Mật khẩu/khóa nằm cứng trong code → chuyển sang biến môi trường.

## Chưa kiểm chứng / hạn chế
- **App Android: tôi không build được** (không có Android SDK). Các sửa đổi nhỏ và đã đọc lại kỹ, nhưng hãy build và chạy thử luồng đăng nhập → đặt → thanh toán trước khi phát hành.
- Đã kiểm chứng tự động: **30 bài test backend** + **3 bài test web chạy với Django thật** (đăng ký → đặt → xác nhận → hóa đơn → lịch sử). Webhook ngân hàng chỉ test bằng dữ liệu giả.
- Khung giờ **không gắn ngày** (thiết kế gốc): chỉ đặt được cho "hôm nay". Muốn đặt trước nhiều ngày cần thêm trường ngày (có migration) và sửa app.
- Tiền chưa tự đối soát nếu không bật webhook.
- Khách chuyển tiền sau khi đơn đã hết hạn: đơn đã bị hủy, cần nhân viên xử lý thủ công.
