from django.urls import path
from . import views

urlpatterns = [
    path('', views.ErrorLogListView.as_view(), name='error-log-list'),
    path('detection-alert/', views.DetectionAlertView.as_view(), name='detection-alert'),
    path('inference-control/', views.InferenceControlView.as_view(), name='inference-control'),
]
