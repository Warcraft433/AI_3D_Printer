from django.urls import path
from . import views

urlpatterns = [
    path('', views.PrintJobListCreateView.as_view(), name='print-job-list'),
    path('clear-finished/', views.ClearFinishedJobsView.as_view(), name='clear-finished'),
    path('active-job/', views.ActivePrintJobView.as_view(), name='active-job'),
    path('<int:pk>/', views.PrintJobDetailView.as_view(), name='print-job-detail'),
    path('<int:pk>/start-print/', views.StartPrintView.as_view(), name='start-print'),
    path('<int:pk>/resume/', views.PauseResumeView.as_view(), name='resume-print'),
    path('<int:pk>/download-gcode/', views.DownloadGCodeView.as_view(), name='download-gcode'),
]
