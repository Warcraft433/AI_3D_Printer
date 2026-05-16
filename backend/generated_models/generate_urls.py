from django.urls import path
from .generate_views import TextGenerateView, ImageGenerateView, ShapeModelStatusView

urlpatterns = [
    path('text/',   TextGenerateView.as_view(),       name='generate-text'),
    path('image/',  ImageGenerateView.as_view(),      name='generate-image'),
    path('status/', ShapeModelStatusView.as_view(),   name='shape-status'),
]
