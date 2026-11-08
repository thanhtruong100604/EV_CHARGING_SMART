/**
 * Kiểm thử tích hợp: web ↔ Django THẬT.
 *   1. Chạy backend:  python manage.py runserver 8000   (có sẵn tài khoản nhân viên "boss"/"secret123" và ít nhất 1 trạm)
 *   2. Chạy test:     E2E_API=http://localhost:8000/api VITE_API_URL=http://localhost:8000/api npm run test:e2e
 */
import { describe, expect, it } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App.jsx";
import { AuthProvider } from "../src/context/AuthContext.jsx";
import { ToastProvider } from "../src/context/ToastContext.jsx";
import { api } from "../src/services/api.js";
import { setSession } from "../src/services/http.js";

const API = process.env.E2E_API;
const d = API ? describe : describe.skip;
const uniq = () => "u" + Math.random().toString(36).slice(2, 8);

async function staffToken() {
  const r = await fetch(`${API}/api-token-auth/`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "boss", password: "secret123" }),
  });
  return (await r.json()).token;
}
async function staffConfirm(id) {
  const token = await staffToken();
  const r = await fetch(`${API}/bookings/${id}/confirm_payment/`, { method: "POST", headers: { Authorization: `Token ${token}` } });
  expect(r.status).toBe(200);
}

d("API layer (dùng chung với app Android)", () => {
  it("đăng ký → đặt chỗ → thanh toán → lịch sử", async () => {
    setSession(null);
    const name = uniq();
    const reg = await api.register({ username: name, email: `${name}@x.com`, password: "abcdef" });
    setSession({ token: reg.token, username: reg.username });

    const stations = await api.stations();
    expect(stations.length).toBeGreaterThan(0);
    expect(typeof stations[0].lat).toBe("number");

    const slots = await api.slots(stations[0].id);
    expect(slots[0].slot_code).toBe("A1");

    const b = await api.createBooking({ stationId: stations[0].id, slotId: slots[0].id, hour: 22 });
    expect(b.status).toBe("Quick_Booking");
    expect(b.user_id).toBe(name);
    expect(b.time_label).toBe("22:00 – 24:00");

    expect(await api.bookedHours(stations[0].id, slots[0].id)).toContain(22);
    await expect(api.createBooking({ stationId: stations[0].id, slotId: slots[0].id, hour: 22 }))
      .rejects.toMatchObject({ status: 409 });

    const pay = await api.paymentInfo(b.id);
    expect(pay.content).toBe(`DatCho_${b.id}`);
    expect(pay.qr_url).toContain("vietqr");

    await staffConfirm(b.id);
    expect((await api.booking(b.id)).status).toBe("Confirmed");
    expect((await api.history(name))).toHaveLength(1);
    expect(await api.stats(name)).toMatchObject({ total: 1, completed: 1, cancelled: 0 });
  });

  it("người khác không đọc/hủy được đơn của mình (token)", async () => {
    const a = uniq(), c = uniq();
    const ra = await api.register({ username: a, email: "", password: "abcdef" });
    setSession({ token: ra.token, username: a });
    const st = (await api.stations())[0];
    const slot = (await api.slots(st.id))[1];
    const mine = await api.createBooking({ stationId: st.id, slotId: slot.id, hour: 22 }); // khối 22–24 luôn còn đặt được trong ngày
    const rc = await api.register({ username: c, email: "", password: "abcdef" });
    setSession({ token: rc.token, username: c });
    await expect(api.booking(mine.id)).rejects.toMatchObject({ status: 403 });
    await expect(api.cancel(mine.id)).rejects.toMatchObject({ status: 403 });
  });
});

d("Giao diện web (jsdom)", () => {
  it("đăng ký, chọn trạm/ô/giờ, thanh toán, xem hóa đơn, lịch sử, hồ sơ", async () => {
    setSession(null);
    const user = userEvent.setup();
    const name = uniq();
    render(<ToastProvider><AuthProvider><App /></AuthProvider></ToastProvider>);

    // --- đăng ký
    await user.click(await screen.findByRole("button", { name: /Chưa có tài khoản/ }));
    await user.type(screen.getByLabelText(/Tên đăng nhập/), name);
    await user.type(screen.getByLabelText(/Email/), `${name}@x.com`);
    await user.type(screen.getByLabelText(/Mật khẩu/), "abcdef");
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    // --- danh sách trạm từ Django thật
    const card = await screen.findByRole("button", { name: /Trạm sạc Mỏ Địa Chất/ }, { timeout: 30000 });
    await user.click(card);

    // --- chọn ô & khung giờ
    const sheet = await screen.findByLabelText(/Chi tiết Trạm sạc Mỏ Địa Chất/);
    await user.click(await within(sheet).findByRole("button", { name: "A3" }));
    await user.click(await within(sheet).findByRole("button", { name: "22:00 – 24:00" }));
    await user.click(within(sheet).getByRole("button", { name: /Đặt ô A3/ }));

    // --- giữ chỗ & thanh toán
    const dialog = await screen.findByRole("dialog", { name: /Giữ chỗ & thanh toán/ });
    expect(await within(dialog).findByAltText(/Mã QR/)).toBeTruthy();
    expect(within(dialog).getByText(/DatCho_\d+/)).toBeTruthy();
    const bookingId = Number(within(dialog).getByText(/DatCho_\d+/).textContent.split("_")[1]);

    // nhân viên xác nhận đã nhận tiền → web tự chuyển sang hóa đơn (polling 4s)
    await staffConfirm(bookingId);
    const invoice = await screen.findByRole("dialog", { name: "Hóa đơn" }, { timeout: 15000 });
    expect(within(invoice).getByText(/ĐÃ THANH TOÁN/)).toBeTruthy();
    expect(within(invoice).getByText("A3")).toBeTruthy();
    await user.click(within(invoice).getAllByRole("button", { name: "Đóng" }).at(-1));

    // --- lịch sử + hồ sơ
    await user.click(screen.getByRole("button", { name: "Lịch sử đặt chỗ" }));
    const hist = await screen.findByRole("dialog", { name: /Lịch sử đặt chỗ/ });
    expect(await within(hist).findByText(/Trạm sạc Mỏ Địa Chất/)).toBeTruthy();
    expect(within(hist).getByText("Đã xác nhận")).toBeTruthy();
    await user.click(within(hist).getAllByRole("button", { name: "Đóng" })[0]);

    await user.click(screen.getByRole("button", { name: "Hồ sơ" }));
    const prof = await screen.findByRole("dialog", { name: /Hồ sơ/ });
    await waitFor(() => expect(within(prof).getAllByText("1").length).toBeGreaterThanOrEqual(2));
    await user.click(within(prof).getByRole("button", { name: "Đăng xuất" }));
    await screen.findByRole("button", { name: "Đăng nhập" });
    cleanup();
  });
});
