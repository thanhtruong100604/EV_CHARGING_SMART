from django.conf import settings
from django.contrib.auth import authenticate
from django.db.models import Count, Q
from django.utils import timezone
from rest_framework import decorators, generics, status, viewsets
from rest_framework.authtoken.models import Token
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.exceptions import NotAuthenticated, PermissionDenied
from rest_framework.permissions import AllowAny, IsAdminUser
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from . import services
from .models import Booking, ChargingSlot, ChargingStation, TimeSlot, SLOT_HOURS
from .serializers import (
    BookingSerializer,
    ChargingSlotSerializer,
    ChargingStationSerializer,
    UserSerializer,
)
from .services import BookingError


# ------------------------------------------------------------------ helpers
def acting_user_id(request, required=True):
    """
    Xác định người dùng đang thao tác.
    - Có token  → dùng username của token (không tin user_id client gửi lên).
    - Không token → chế độ tương thích app cũ: lấy user_id client gửi lên,
      trừ khi bật REQUIRE_AUTH=True (khuyến nghị sau khi cả app và web đã gửi token).
    """
    if request.user and request.user.is_authenticated:
        return request.user.username
    if settings.REQUIRE_AUTH:
        raise NotAuthenticated("Vui lòng đăng nhập")
    uid = request.data.get("user_id") if request.method != "GET" else None
    uid = uid or request.query_params.get("user_id")
    if required and not uid:
        raise BookingError("Thiếu user_id", 400)
    return uid


def check_owner(request, booking):
    """Chủ đơn hoặc nhân viên mới được thao tác trên đơn."""
    user = request.user
    if user and user.is_authenticated:
        if user.is_staff or booking.user_id == user.username:
            return
        raise PermissionDenied("Đơn đặt chỗ này không thuộc về bạn")
    if settings.REQUIRE_AUTH:
        raise NotAuthenticated("Vui lòng đăng nhập")


def error_response(exc):
    return Response({"error": exc.message, "detail": exc.message}, status=exc.status_code)


def station_queryset():
    """Số ô trống & số khung giờ còn trống, tính bằng 1 truy vấn (tránh N+1)."""
    hour = services.local_now().hour
    return ChargingStation.objects.annotate(
        free_slots=Count("slots", filter=Q(slots__is_available=True), distinct=True),
        free_time_slots=Count(
            "time_slots",
            filter=Q(time_slots__is_available=True, time_slots__start_hour__gt=hour - SLOT_HOURS),
            distinct=True,
        ),
    ).order_by("id")


# --------------------------------------------------------------------- auth
class RegisterView(generics.CreateAPIView):
    serializer_class = UserSerializer
    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "auth"

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        user = serializer.save()
        token, _ = Token.objects.get_or_create(user=user)
        return Response(
            {"id": user.id, "username": user.username, "email": user.email, "token": token.key},
            status=status.HTTP_201_CREATED,
        )


class LoginView(APIView):
    """POST /api/api-token-auth/ → {token, user_id, username, email}. App cũ chỉ đọc 'token' nên vẫn chạy."""

    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "auth"

    def post(self, request):
        username = (request.data.get("username") or "").strip()
        password = request.data.get("password") or ""
        user = authenticate(request=request, username=username, password=password)
        if not user:
            return Response(
                {"non_field_errors": ["Sai tài khoản hoặc mật khẩu"]},
                status=status.HTTP_400_BAD_REQUEST,
            )
        token, _ = Token.objects.get_or_create(user=user)
        return Response(
            {
                "token": token.key,
                "user_id": user.id,
                "username": user.username,
                "email": user.email,
                "is_staff": user.is_staff,
            }
        )


# -------------------------------------------------------------------- thống kê
@api_view(["GET"])
def user_booking_stats(request):
    services.release_expired_and_finished()
    try:
        user_id = acting_user_id(request)
    except BookingError as exc:
        return error_response(exc)
    qs = Booking.objects.filter(user_id=user_id)
    return Response(
        {
            "total": qs.count(),
            # "completed" = đã thanh toán thành công (đang giữ chỗ hoặc đã sạc xong)
            "completed": qs.filter(status__in=["Confirmed", "Completed"]).count(),
            "cancelled": qs.filter(status="Cancelled").count(),
        }
    )


# ------------------------------------------------------------------- trạm sạc
class ChargingStationViewSet(viewsets.ModelViewSet):
    serializer_class = ChargingStationSerializer

    def get_queryset(self):
        return station_queryset()

    def get_permissions(self):
        if self.action in ("list", "retrieve", "slots"):
            return [AllowAny()]
        return [IsAdminUser()]  # thêm/sửa/xoá trạm chỉ dành cho quản trị

    def list(self, request, *args, **kwargs):
        services.release_expired_and_finished()
        return super().list(request, *args, **kwargs)

    @decorators.action(detail=True, methods=["get"])
    def slots(self, request, pk=None):
        """GET /api/stations/{id}/slots/"""
        services.release_expired_and_finished()
        slots = ChargingSlot.objects.filter(station_id=pk).order_by("slot_code")
        return Response(ChargingSlotSerializer(slots, many=True).data)

    @decorators.action(detail=False, methods=["post"])
    def force_reset_timeslot(self, request):
        station_id = request.data.get("station_id")
        slot_id = request.data.get("slot_id")
        hour = request.data.get("hour")
        qs = TimeSlot.objects.filter(station_id=station_id, slot_id=slot_id)
        if hour is not None:
            qs = qs.filter(start_hour=hour)
        count = qs.update(is_available=True)
        return Response({"message": f"Đã reset {count} khung giờ", "reset_hour": hour})


# ------------------------------------------------------------------- đặt chỗ
class BookingViewSet(viewsets.ModelViewSet):
    serializer_class = BookingSerializer

    def get_permissions(self):
        if self.action in ("update", "partial_update", "destroy", "force_status"):
            return [IsAdminUser()]
        return [AllowAny()]

    def get_queryset(self):
        qs = Booking.objects.select_related("station", "slot").order_by("-booking_time")
        user = self.request.user
        if user.is_authenticated and user.is_staff and not self.request.query_params.get("user_id"):
            return qs  # nhân viên xem tất cả
        try:
            uid = acting_user_id(self.request)
        except BookingError:
            return qs.none()  # không có user_id → không lộ dữ liệu của người khác
        return qs.filter(user_id=uid)

    # ---- đọc
    def list(self, request, *args, **kwargs):
        services.release_expired_and_finished()
        return super().list(request, *args, **kwargs)

    def retrieve(self, request, *args, **kwargs):
        services.release_expired_and_finished()
        booking = generics.get_object_or_404(Booking.objects.select_related("station", "slot"), pk=kwargs["pk"])
        check_owner(request, booking)
        return Response(self.get_serializer(booking).data)

    # ---- tạo đơn (app: POST /api/bookings/ — hỗ trợ cả đặt ngay & theo khung giờ)
    def create(self, request, *args, **kwargs):
        try:
            booking = services.create_booking(
                user_id=acting_user_id(request),
                station_id=request.data.get("station"),
                slot_id=request.data.get("slot"),
                scheduled_hour=request.data.get("scheduled_hour"),
                hold_minutes=request.data.get("hold_minutes"),
            )
        except BookingError as exc:
            return error_response(exc)
        data = self.get_serializer(booking).data
        data["message"] = "Đặt chỗ thành công!"
        return Response(data, status=status.HTTP_201_CREATED)

    @decorators.action(detail=False, methods=["post"])
    def create_booking_with_slot(self, request):
        return self.create(request)

    # ---- trạng thái
    def _get_booking(self, request, pk):
        booking = generics.get_object_or_404(Booking.objects.select_related("station"), pk=pk)
        check_owner(request, booking)
        return booking

    @decorators.action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        booking = self._get_booking(request, pk)
        is_staff = request.user.is_authenticated and request.user.is_staff
        if booking.status == "Confirmed" and not is_staff:
            return Response(
                {"error": "Đơn đã thanh toán, vui lòng liên hệ trạm để hủy/hoàn tiền"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if booking.status == "Quick_Booking" or (booking.status == "Confirmed" and is_staff):
            booking.status = "Cancelled"
            booking.save()
        return Response({"message": "Đã hủy", "status": booking.status})

    @decorators.action(detail=True, methods=["post"])
    def update_status(self, request, pk=None):
        """App gọi khi hết giờ thanh toán: POST /api/bookings/{id}/update_status/ status=Cancelled"""
        booking = self._get_booking(request, pk)
        new_status = request.data.get("status")
        is_staff = request.user.is_authenticated and request.user.is_staff
        allowed = {"Cancelled", "Completed", "Confirmed"} if is_staff else {"Cancelled"}
        if new_status not in allowed:
            return Response({"error": "Trạng thái không hợp lệ"}, status=status.HTTP_400_BAD_REQUEST)
        if new_status == "Cancelled" and booking.status == "Confirmed" and not is_staff:
            return Response({"error": "Đơn đã thanh toán, không thể tự hủy"}, status=400)
        if booking.status not in ("Cancelled", "Completed"):
            booking.status = new_status
            booking.save()
        return Response({"status": booking.status})

    @decorators.action(detail=False, methods=["post"])
    def check_in(self, request):
        try:
            user_id = acting_user_id(request)
        except BookingError as exc:
            return error_response(exc)
        booking = (
            Booking.objects.filter(
                user_id=user_id,
                station_id=request.data.get("station_id"),
                status="Quick_Booking",
                is_checked_in=False,
            )
            .order_by("-booking_time")
            .first()
        )
        if not booking:
            return Response({"error": "Không tìm thấy thông tin đặt chỗ!"}, status=404)
        if booking.expiry_time and booking.expiry_time < timezone.now():
            return Response({"error": "Thời gian giữ chỗ đã hết hạn!"}, status=400)
        booking.status = "Confirmed"
        booking.is_checked_in = True
        booking.save()
        return Response({"message": "Check-in thành công! Bắt đầu sạc.", "status": booking.status})

    @decorators.action(detail=False, methods=["post"])
    def complete_charging(self, request):
        try:
            user_id = acting_user_id(request)
        except BookingError as exc:
            return error_response(exc)
        qs = Booking.objects.filter(user_id=user_id, status="Confirmed")
        if request.data.get("booking_id"):
            qs = qs.filter(pk=request.data["booking_id"])
        booking = qs.order_by("-booking_time").first()
        if not booking:
            return Response({"error": "Không tìm thấy phiên sạc!"}, status=400)
        booking.status = "Completed"
        booking.save()  # tự trả chỗ
        return Response({"message": "Đã hoàn tất sạc và trả chỗ!"})

    # ---- thanh toán
    @decorators.action(detail=True, methods=["get"])
    def payment_info(self, request, pk=None):
        """Thông tin chuyển khoản + link ảnh VietQR — app và web dùng chung."""
        booking = self._get_booking(request, pk)
        return Response(services.payment_info(booking))

    @decorators.action(detail=True, methods=["post"])
    def confirm_payment(self, request, pk=None):
        """
        Chỉ nhân viên mới được chuyển đơn sang Confirmed (hoặc webhook ngân hàng).
        Client gọi lại khi đơn đã Confirmed thì trả 200 để app cũ chạy bình thường.
        """
        booking = self._get_booking(request, pk)
        if booking.status == "Confirmed":
            return Response({"message": "Đơn đặt chỗ đã được xác nhận.", "booking_id": booking.id})
        is_staff = request.user.is_authenticated and request.user.is_staff
        if not is_staff:
            return Response({"error": "Đơn chưa được thanh toán"}, status=status.HTTP_403_FORBIDDEN)
        if booking.status != "Quick_Booking":
            return Response({"error": "Chỉ xác nhận được đơn đang giữ chỗ"}, status=400)
        booking.status = "Confirmed"
        booking.save()
        return Response({"message": "Thanh toán thành công! Chỗ sạc đã được giữ.", "booking_id": booking.id})

    @decorators.action(detail=False, methods=["get"])
    def booked_hours(self, request):
        """Các giờ KHÔNG đặt được của một ô: đã có người đặt hoặc đã qua giờ."""
        services.release_expired_and_finished()
        station_id = request.query_params.get("station_id")
        slot_id = request.query_params.get("slot_id")
        if not station_id or not slot_id:
            return Response({"error": "Thiếu tham số"}, status=400)
        now = services.local_now()
        hours = set(
            TimeSlot.objects.filter(station_id=station_id, slot_id=slot_id, is_available=False)
            .values_list("start_hour", flat=True)
        )
        hours.update(h for h in range(0, 24, SLOT_HOURS) if services.hour_is_past(h, now))
        return Response(sorted(hours))


# -------------------------------------------------- webhook ngân hàng (tuỳ chọn)
@api_view(["POST"])
@permission_classes([AllowAny])
@throttle_classes([])
def payment_webhook(request):
    """
    POST /api/payments/webhook/ — dành cho SePay / Casso / dịch vụ báo biến động số dư.
    Cấu hình biến môi trường PAYMENT_WEBHOOK_SECRET rồi khai báo cùng secret ở dịch vụ đó
    (gửi qua header 'Authorization: Apikey <secret>' hoặc 'X-Webhook-Secret').
    """
    secret = settings.PAYMENT_WEBHOOK_SECRET
    if not secret:
        return Response({"error": "Webhook chưa được bật"}, status=status.HTTP_404_NOT_FOUND)
    auth = request.headers.get("Authorization", "")
    given = auth.split(" ", 1)[1].strip() if auth.lower().startswith("apikey ") else request.headers.get("X-Webhook-Secret", "")
    if given != secret:
        return Response({"error": "Sai secret"}, status=status.HTTP_401_UNAUTHORIZED)

    data = request.data
    if str(data.get("transferType", "in")).lower() not in ("in", "credit"):
        return Response({"success": True, "message": "Bỏ qua giao dịch tiền ra"})
    content = data.get("content") or data.get("description") or ""
    amount = data.get("transferAmount") or data.get("amount") or 0
    booking, message = services.confirm_from_transfer(content, amount)
    return Response({"success": booking is not None, "message": message})
