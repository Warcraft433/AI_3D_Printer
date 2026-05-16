from django.urls import path
from . import views

urlpatterns = [
    path('stats/', views.DashboardStatsView.as_view(), name='dashboard-stats'),
    path('printer-status/', views.PrinterStatusView.as_view(), name='printer-status'),
    path('raw-test-feed/', views.raw_test_feed, name='raw-test-feed'),
    path('video-feed/', views.video_feed, name='video-feed'),
    path('start-monitoring/', views.StartMonitoringView.as_view(), name='start-monitoring'),
    path('stop-monitoring/', views.StopMonitoringView.as_view(), name='stop-monitoring'),
    path('monitoring-status/', views.MonitoringStatusView.as_view(), name='monitoring-status'),
]
