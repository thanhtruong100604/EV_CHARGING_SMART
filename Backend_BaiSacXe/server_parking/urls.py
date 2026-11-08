from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path


def health(request):
    """Render dùng để kiểm tra server sống; web/app gọi để đánh thức server khi đang ngủ."""
    return JsonResponse({"status": "ok"})


urlpatterns = [
    path("", health),
    path("health/", health),
    path("admin/", admin.site.urls),
    path("api/", include("charging_api.urls")),
]
