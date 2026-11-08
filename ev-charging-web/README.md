# EV Charging Smart – Web

Web bản đồ trạm sạc xe điện, dùng chung API với app mobile.
Công nghệ: **React + Vite + Leaflet + OpenStreetMap** (bản đồ miễn phí, không cần API key).

## Chạy thử
```bash
npm install
npm run dev        # mở http://localhost:5173
```
Mặc định chưa có API nên web hiển thị 5 trạm mẫu.

## Nối với API của app
1. Mở file `.env`, điền địa chỉ backend:
   ```
   VITE_API_URL=https://api.ten-mien-cua-ban.com/api
   VITE_STATIONS_PATH=/stations
   ```
2. Nếu tên field JSON khác (`name`, `lat`, `lng`, `status`, `power`, `connector`, `price`)
   → chỉ sửa hàm `normalize()` trong `src/services/api.js`.
3. `status` nhận 3 giá trị: `free`, `busy`, `off`.
4. Backend phải bật **CORS** cho tên miền của web.

## Cấu trúc
```
src/
  config.js            cấu hình, màu trạng thái, tâm bản đồ
  services/api.js      gọi API + chuẩn hoá dữ liệu  ← sửa khi đổi API
  hooks/               useStations (tải + tự làm mới), useGeolocation
  components/          Header, FilterBar, StationList, MapView
  utils/distance.js    tính khoảng cách, format tiền
  data/mockStations.js dữ liệu mẫu
```

## Build & triển khai
```bash
npm run build      # ra thư mục dist/
```
- **GitLab Pages**: file `.gitlab-ci.yml` đã có sẵn, push lên nhánh `main` là tự deploy miễn phí.
- Hoặc đưa thư mục `dist/` lên Netlify, Vercel, Cloudflare Pages, Firebase Hosting.

## Lưu ý bản đồ
Server tile công cộng của OpenStreetMap chỉ phù hợp lượng truy cập nhỏ/vừa.
Khi web đông người, đổi `url` trong `src/components/MapView.jsx` sang nhà cung cấp
có gói miễn phí (MapTiler, Stadia Maps...).

## Nếu bản đồ bị xám hoặc có chữ "API KEY REQUIRED"
Một số nguồn tile (như CARTO) đã yêu cầu key. Web mặc định dùng Esri (không cần key)
và tự chuyển nguồn khi lỗi. Muốn bản đồ đẹp, ổn định nhất: đăng ký MapTiler miễn phí
(maptiler.com) rồi điền `VITE_MAPTILER_KEY` trong `.env`.
