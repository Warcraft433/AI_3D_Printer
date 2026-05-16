from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from dashboard import views as dashboard_views

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/auth/', include('accounts.urls')),
    path('api/models/', include('generated_models.urls')),
    path('api/print-jobs/', include('print_jobs.urls')),
    path('api/error-logs/', include('error_logs.urls')),
    path('api/dashboard/', include('dashboard.urls')),
    path('api/generate/', include('generated_models.generate_urls')),
    path('api/raw-test-feed/', dashboard_views.raw_test_feed, name='root-raw-test-feed'),
] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
