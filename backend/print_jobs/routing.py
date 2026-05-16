from django.urls import re_path
from . import consumers

websocket_urlpatterns = [
    re_path(r'ws/print-jobs/(?P<job_id>\w+)/$', consumers.PrintStatusConsumer.as_asgi()),
]
