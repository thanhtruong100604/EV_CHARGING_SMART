"""
Django settings cho server_parking.

Mọi thông tin nhạy cảm (SECRET_KEY, mật khẩu database, tài khoản ngân hàng...) đọc từ BIẾN MÔI TRƯỜNG,
KHÔNG viết trong code. Trên Render: Dashboard → service → Environment (xem README_BACKEND.md).
"""
import os
import sys
from pathlib import Path

import dj_database_url
from django.core.exceptions import ImproperlyConfigured

BASE_DIR = Path(__file__).resolve().parent.parent


def env_bool(name, default=False):
    return os.environ.get(name, str(default)).strip().lower() in ("1", "true", "yes", "on")


def env_list(name, default=""):
    return [x.strip() for x in os.environ.get(name, default).split(",") if x.strip()]


ON_RENDER = bool(os.environ.get("RENDER"))  # Render tự đặt biến này
DEBUG = env_bool("DEBUG", default=not ON_RENDER)

# ---- Bảo mật ---------------------------------------------------------------
SECRET_KEY = os.environ.get("SECRET_KEY", "")
if not SECRET_KEY:
    if ON_RENDER:
        raise ImproperlyConfigured("Chưa đặt biến môi trường SECRET_KEY trên Render (Environment → Add).")
    SECRET_KEY = "dev-only-secret-key-do-not-use-in-production"  # chỉ dùng khi chạy trên máy cá nhân

ALLOWED_HOSTS = ["localhost", "127.0.0.1", ".onrender.com"] + env_list("ALLOWED_HOSTS")
CSRF_TRUSTED_ORIGINS = ["https://*.onrender.com"] + env_list("CSRF_TRUSTED_ORIGINS")

# Web được phép gọi API. Để trống = cho tất cả (app Android không bị ảnh hưởng bởi CORS).
# Khi web đã lên GitLab Pages, có thể siết lại: CORS_ALLOWED_ORIGINS=https://<tên>.gitlab.io
CORS_ALLOWED_ORIGINS = env_list("CORS_ALLOWED_ORIGINS")
CORS_ALLOW_ALL_ORIGINS = not CORS_ALLOWED_ORIGINS

if ON_RENDER:
    # Render đứng sau proxy HTTPS → tin header X-Forwarded-Proto
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
    SECURE_SSL_REDIRECT = env_bool("SECURE_SSL_REDIRECT", True)
    # Trang kiểm tra sống (health check) không bị chuyển hướng sang https
    SECURE_REDIRECT_EXEMPT = [r"^health/?$", r"^$"]
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True

# ---- Ứng dụng --------------------------------------------------------------
INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "corsheaders",
    "rest_framework.authtoken",
    "charging_api",
]

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "rest_framework.authentication.TokenAuthentication",
    ],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.AllowAny"],
    "DEFAULT_THROTTLE_RATES": {"auth": "30/min"},
}

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "server_parking.urls"
WSGI_APPLICATION = "server_parking.wsgi.application"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

# ---- Database --------------------------------------------------------------
# Render: đặt biến DATABASE_URL (dán Internal hoặc External Database URL của Postgres).
# Máy local: không đặt → tự dùng SQLite (db.sqlite3).
DATABASE_URL = os.environ.get("DATABASE_URL", "")
if not DATABASE_URL and ON_RENDER:
    raise ImproperlyConfigured(
        "Chưa đặt DATABASE_URL trên Render. Nếu bỏ qua, server sẽ chạy trên SQLite tạm "
        "và MẤT dữ liệu mỗi lần deploy. Vào Environment → Add → DATABASE_URL."
    )
DATABASES = {
    "default": dj_database_url.config(
        default=f"sqlite:///{BASE_DIR / 'db.sqlite3'}",
        conn_max_age=600,
        conn_health_checks=True,
    )
}

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator", "OPTIONS": {"min_length": 6}},
]

# ---- Ngôn ngữ / múi giờ ----------------------------------------------------
LANGUAGE_CODE = "vi"
TIME_ZONE = "Asia/Ho_Chi_Minh"  # khung giờ đặt chỗ tính theo giờ Việt Nam
USE_I18N = True
USE_TZ = True

# ---- Static (WhiteNoise) ---------------------------------------------------
STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
STORAGES = {
    "default": {"BACKEND": "django.core.files.storage.FileSystemStorage"},
    # Không dùng bản "Manifest" để quên chạy collectstatic cũng không làm trang admin lỗi 500
    "staticfiles": {"BACKEND": "whitenoise.storage.CompressedStaticFilesStorage"},
}

# ---- Log ra console để xem trên Render → Logs -------------------------------
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {"simple": {"format": "[{levelname}] {name}: {message}", "style": "{"}},
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "simple"}},
    "loggers": {
        "django.request": {"handlers": ["console"], "level": "WARNING", "propagate": False},
        "charging_api": {"handlers": ["console"], "level": "INFO", "propagate": False},
    },
}

# ---- Nghiệp vụ đặt chỗ -----------------------------------------------------
BOOKING_HOLD_MINUTES = int(os.environ.get("BOOKING_HOLD_MINUTES", 10))  # thời gian giữ chỗ chờ thanh toán
BOOKING_AMOUNT = int(os.environ.get("BOOKING_AMOUNT", 100000))  # VNĐ / lượt sạc
MAX_PENDING_HOLDS = int(os.environ.get("MAX_PENDING_HOLDS", 2))  # tối đa số đơn chưa thanh toán / người
QUICK_CONFIRMED_MAX_HOURS = int(os.environ.get("QUICK_CONFIRMED_MAX_HOURS", 3))

# Bật True khi CẢ app và web đã gửi token → chặn người lạ đọc/hủy đơn của người khác
REQUIRE_AUTH = env_bool("REQUIRE_AUTH", False)

# ---- Thanh toán chuyển khoản (VietQR) --------------------------------------
PAYMENT_BANK_BIN = os.environ.get("PAYMENT_BANK_BIN", "970415")  # 970415 = VietinBank
PAYMENT_ACCOUNT_NO = os.environ.get("PAYMENT_ACCOUNT_NO", "108876696755")
PAYMENT_ACCOUNT_NAME = os.environ.get("PAYMENT_ACCOUNT_NAME", "EV CHARGING SMART")
PAYMENT_WEBHOOK_SECRET = os.environ.get("PAYMENT_WEBHOOK_SECRET", "")  # để trống = tắt webhook
