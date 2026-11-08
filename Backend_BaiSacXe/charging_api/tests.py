from datetime import datetime, timedelta
from unittest import mock
from zoneinfo import ZoneInfo

from django.contrib.auth.models import User
from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase

from . import services
from .models import Booking, ChargingSlot, ChargingStation, TimeSlot

VN = ZoneInfo("Asia/Ho_Chi_Minh")


def at(hour, minute=0, day=10):
    """Giả lập 'bây giờ' là <hour>:<minute> giờ Việt Nam."""
    return datetime(2026, 11, day, hour, minute, tzinfo=VN)


class BaseCase(APITestCase):
    def setUp(self):
        services._last_sweep = 0.0
        self.station = ChargingStation.objects.create(
            name="Trạm A", ward="Hoàn Kiếm", address="Hà Nội", latitude=21.0285, longitude=105.8542, total_slots=3
        )
        self.slot = ChargingSlot.objects.filter(station=self.station).order_by("slot_code").first()
        self.user = User.objects.create_user("alice", "a@x.com", "secret123")
        self.staff = User.objects.create_user("boss", "b@x.com", "secret123", is_staff=True)

    def now(self, hour, minute=0, day=10):
        return mock.patch("django.utils.timezone.now", return_value=at(hour, minute, day))

    def book(self, user_id="alice", hour=14, slot=None, **extra):
        return self.client.post(
            "/api/bookings/create_booking_with_slot/",
            {"user_id": user_id, "station": self.station.id, "slot": (slot or self.slot).id,
             "scheduled_hour": hour, "status": "Quick_Booking", **extra},
            format="json",
        )

    def token(self, user):
        from rest_framework.authtoken.models import Token
        t, _ = Token.objects.get_or_create(user=user)
        return f"Token {t.key}"


class AuthTests(BaseCase):
    def test_register_and_login(self):
        r = self.client.post("/api/register/", {"username": "bob", "email": "b@b.com", "password": "abcdef"}, format="json")
        self.assertEqual(r.status_code, 201)
        self.assertIn("token", r.data)
        r = self.client.post("/api/api-token-auth/", {"username": "bob", "password": "abcdef"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["username"], "bob")
        self.assertIn("token", r.data)

    def test_wrong_password(self):
        r = self.client.post("/api/api-token-auth/", {"username": "alice", "password": "nope"}, format="json")
        self.assertEqual(r.status_code, 400)

    def test_register_short_password_rejected(self):
        r = self.client.post("/api/register/", {"username": "bob", "email": "b@b.com", "password": "123"}, format="json")
        self.assertEqual(r.status_code, 400)


class StationTests(BaseCase):
    def test_list_shape_matches_android(self):
        with self.now(9):
            r = self.client.get("/api/stations/")
        self.assertEqual(r.status_code, 200)
        s = r.data[0]
        for key in ("id", "name", "ward", "address", "latitude", "longitude", "total_slots",
                    "available_slots", "real_available_time_slots"):
            self.assertIn(key, s)
        self.assertIsInstance(s["latitude"], float)
        self.assertEqual(s["available_slots"], 3)
        # 3 ô × 12 khung - các khung đã kết thúc trước 9h (0-2,2-4,4-6,6-8 → 4 khung × 3 ô)
        self.assertEqual(s["real_available_time_slots"], 3 * 12 - 4 * 3)

    def test_anonymous_cannot_delete_or_create_station(self):
        self.assertEqual(self.client.delete(f"/api/stations/{self.station.id}/").status_code, 401)
        r = self.client.post("/api/stations/", {"name": "x", "address": "y", "total_slots": 1}, format="json")
        self.assertEqual(r.status_code, 401)

    def test_normal_user_cannot_delete_station(self):
        self.client.credentials(HTTP_AUTHORIZATION=self.token(self.user))
        self.assertEqual(self.client.delete(f"/api/stations/{self.station.id}/").status_code, 403)

    def test_slots_endpoint(self):
        r = self.client.get(f"/api/stations/{self.station.id}/slots/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual([s["slot_code"] for s in r.data], ["A1", "A2", "A3"])


class BookingFlowTests(BaseCase):
    def test_book_then_conflict(self):
        with self.now(9):
            r = self.book("alice", 14)
            self.assertEqual(r.status_code, 201, r.data)
            self.assertEqual(r.data["status"], "Quick_Booking")
            self.assertEqual(r.data["time_label"], "14:00 – 16:00")
            r2 = self.book("carol", 14)
            self.assertEqual(r2.status_code, 409)
            self.assertIn("error", r2.data)
            # khung giờ khác của cùng ô vẫn đặt được
            self.assertEqual(self.book("carol", 16).status_code, 201)

    def test_cannot_book_past_hour(self):
        with self.now(15):
            r = self.book("alice", 10)
        self.assertEqual(r.status_code, 400)

    def test_current_block_still_bookable(self):
        with self.now(15, 30):
            self.assertEqual(self.book("alice", 14).status_code, 201)  # 14-16 chưa kết thúc

    def test_odd_hour_rejected(self):
        with self.now(9):
            self.assertEqual(self.book("alice", 13).status_code, 400)

    def test_booked_hours_includes_booked_and_past(self):
        with self.now(9):
            self.book("alice", 14)
            r = self.client.get("/api/bookings/booked_hours/", {"station_id": self.station.id, "slot_id": self.slot.id})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data, [0, 2, 4, 6, 14])

    def test_hold_expires_and_releases(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
        with self.now(9, 11):  # sau 11 phút
            self.client.get("/api/stations/")
        b = Booking.objects.get(pk=bid)
        self.assertEqual(b.status, "Cancelled")
        self.assertTrue(TimeSlot.objects.get(station=self.station, slot=self.slot, start_hour=14).is_available)

    def test_pending_hold_limit(self):
        with self.now(9):
            slots = list(ChargingSlot.objects.filter(station=self.station))
            self.assertEqual(self.book("alice", 14, slot=slots[0]).status_code, 201)
            self.assertEqual(self.book("alice", 14, slot=slots[1]).status_code, 201)
            self.assertEqual(self.book("alice", 14, slot=slots[2]).status_code, 400)

    def test_quick_booking_locks_and_cancel_frees_slot(self):
        """App 'Đặt ngay': POST /api/bookings/ với scheduled_hour=-1 (form-urlencoded) + hold_minutes."""
        with self.now(9):
            r = self.client.post("/api/bookings/", {
                "user_id": "alice", "station": self.station.id, "slot": self.slot.id,
                "status": "Quick_Booking", "scheduled_hour": -1, "hold_minutes": 25})
            self.assertEqual(r.status_code, 201, r.data)
            self.slot.refresh_from_db()
            self.assertFalse(self.slot.is_available)
            b = Booking.objects.get(pk=r.data["id"])
            self.assertAlmostEqual((b.expiry_time - timezone.now()).total_seconds(), 25 * 60, delta=5)
            # người khác không giữ trùng ô
            r2 = self.client.post("/api/bookings/", {
                "user_id": "carol", "station": self.station.id, "slot": self.slot.id, "scheduled_hour": -1})
            self.assertEqual(r2.status_code, 409)
            # app gọi update_status khi hết giờ → trả ô lại
            r3 = self.client.post(f"/api/bookings/{b.id}/update_status/", {"status": "Cancelled"})
            self.assertEqual(r3.status_code, 200)
        self.slot.refresh_from_db()
        self.assertTrue(self.slot.is_available)

    def test_hold_minutes_clamped(self):
        with self.now(9):
            r = self.client.post("/api/bookings/", {
                "user_id": "alice", "station": self.station.id, "slot": self.slot.id,
                "scheduled_hour": -1, "hold_minutes": 9999})
            b = Booking.objects.get(pk=r.data["id"])
            self.assertAlmostEqual((b.expiry_time - timezone.now()).total_seconds(), 60 * 60, delta=5)

    def test_cancel_releases_timeslot(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
            r = self.client.post(f"/api/bookings/{bid}/cancel/")
            self.assertEqual(r.status_code, 200)
            self.assertEqual(self.book("carol", 14).status_code, 201)

    def test_double_cancel_does_not_free_someone_elses_slot(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
            self.client.post(f"/api/bookings/{bid}/cancel/")
            self.assertEqual(self.book("carol", 14).status_code, 201)  # carol giữ chỗ
            self.client.post(f"/api/bookings/{bid}/cancel/")  # alice hủy lại lần 2
            self.assertEqual(self.book("dave", 14).status_code, 409)  # chỗ của carol vẫn được giữ


class PaymentTests(BaseCase):
    def test_client_cannot_self_confirm_payment(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
            r = self.client.post(f"/api/bookings/{bid}/confirm_payment/")
        self.assertEqual(r.status_code, 403)
        self.assertEqual(Booking.objects.get(pk=bid).status, "Quick_Booking")

    def test_staff_confirms_then_client_idempotent(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
            self.client.credentials(HTTP_AUTHORIZATION=self.token(self.staff))
            self.assertEqual(self.client.post(f"/api/bookings/{bid}/confirm_payment/").status_code, 200)
            self.client.credentials()
            self.assertEqual(self.client.post(f"/api/bookings/{bid}/confirm_payment/").status_code, 200)
        self.assertEqual(Booking.objects.get(pk=bid).status, "Confirmed")

    def test_payment_info(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
            r = self.client.get(f"/api/bookings/{bid}/payment_info/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["content"], f"DatCho_{bid}")
        self.assertIn("img.vietqr.io", r.data["qr_url"])
        self.assertEqual(r.data["amount"], 100000)

    @override_settings(PAYMENT_WEBHOOK_SECRET="s3cret")
    def test_webhook_confirms(self):
        with self.now(9):
            bid = self.book("alice", 14).data["id"]
            bad = self.client.post("/api/payments/webhook/", {"content": f"DATCHO{bid}", "transferAmount": 100000}, format="json")
            self.assertEqual(bad.status_code, 401)
            h = {"HTTP_AUTHORIZATION": "Apikey s3cret"}
            short = self.client.post("/api/payments/webhook/", {"content": f"DATCHO{bid}", "transferAmount": 5000}, format="json", **h)
            self.assertFalse(short.data["success"])
            ok = self.client.post("/api/payments/webhook/", {"content": f"MB 12345 DATCHO{bid} FT123", "transferAmount": 100000, "transferType": "in"}, format="json", **h)
            self.assertTrue(ok.data["success"], ok.data)
            again = self.client.post("/api/payments/webhook/", {"content": f"DatCho_{bid}", "transferAmount": 100000}, format="json", **h)
            self.assertTrue(again.data["success"])
        self.assertEqual(Booking.objects.get(pk=bid).status, "Confirmed")

    def test_webhook_disabled_by_default(self):
        r = self.client.post("/api/payments/webhook/", {"content": "DatCho_1", "transferAmount": 1}, format="json")
        self.assertEqual(r.status_code, 404)

    def test_confirmed_booking_finishes_after_its_window(self):
        with self.now(9):
            bid = self.book("alice", 10).data["id"]
            Booking.objects.filter(pk=bid).update(status="Confirmed")
        with self.now(12, 5):
            self.client.get("/api/stations/")
        self.assertEqual(Booking.objects.get(pk=bid).status, "Completed")
        self.assertTrue(TimeSlot.objects.get(station=self.station, slot=self.slot, start_hour=10).is_available)


class HistoryAndSecurityTests(BaseCase):
    def test_history_only_own_and_needs_user_id(self):
        with self.now(9):
            self.book("alice", 14)
            self.book("carol", 16)
            r = self.client.get("/api/bookings/", {"user_id": "alice"})
            self.assertEqual([b["user_id"] for b in r.data], ["alice"])
            self.assertEqual(r.data[0]["station_name"], "Trạm A")
            self.assertEqual(r.data[0]["slot_code"], "A1")
            self.assertEqual(self.client.get("/api/bookings/").data, [])  # không lộ đơn người khác

    def test_token_user_cannot_touch_other_users_booking(self):
        with self.now(9):
            bid = self.book("carol", 14).data["id"]
            self.client.credentials(HTTP_AUTHORIZATION=self.token(self.user))  # alice
            self.assertEqual(self.client.post(f"/api/bookings/{bid}/cancel/").status_code, 403)
            self.assertEqual(self.client.get(f"/api/bookings/{bid}/").status_code, 403)

    def test_token_overrides_spoofed_user_id(self):
        self.client.credentials(HTTP_AUTHORIZATION=self.token(self.user))
        with self.now(9):
            r = self.book("someone_else", 14)
        self.assertEqual(r.data["user_id"], "alice")

    @override_settings(REQUIRE_AUTH=True)
    def test_require_auth_blocks_anonymous(self):
        with self.now(9):
            self.assertEqual(self.book("alice", 14).status_code, 401)

    def test_stats(self):
        with self.now(9):
            a = self.book("alice", 14).data["id"]
            b = self.book("alice", 16).data["id"]
            Booking.objects.filter(pk=a).update(status="Confirmed")
            self.client.post(f"/api/bookings/{b}/cancel/")
            r = self.client.get("/api/bookings/stats/", {"user_id": "alice"})
        self.assertEqual(r.data, {"total": 2, "completed": 1, "cancelled": 1})

    def test_health(self):
        self.assertEqual(self.client.get("/health/").json(), {"status": "ok"})
