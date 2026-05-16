from django.urls import path
from . import views

urlpatterns = [
    path('', views.ModelListCreateView.as_view(), name='model-list'),
    path('<int:pk>/', views.ModelDetailView.as_view(), name='model-detail'),
]
