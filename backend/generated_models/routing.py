from django.urls import re_path
from . import consumers

websocket_urlpatterns = [
    re_path(r'ws/generation/(?P<model_id>\w+)/$', consumers.ModelStatusConsumer.as_asgi()),
]
