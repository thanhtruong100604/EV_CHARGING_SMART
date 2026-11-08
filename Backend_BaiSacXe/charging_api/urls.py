from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import (
    BookingViewSet,
    ChargingStationViewSet,
    LoginView,
    RegisterView,
    payment_webhook,
    user_booking_stats,
)

router = DefaultRouter()
router.register(r"stations", ChargingStationViewSet, basename="station")
router.register(r"bookings", BookingViewSet, basename="booking")

urlpatterns = [
    path("bookings/stats/", user_booking_stats, name="booking_stats"),
    path("", include(router.urls)),
    path("register/", RegisterView.as_view(), name="register"),
    path("api-token-auth/", LoginView.as_view(), name="api_token_auth"),
    path("payments/webhook/", payment_webhook, name="payment_webhook"),
]
