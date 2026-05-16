from rest_framework import generics, permissions, views, status
from rest_framework.response import Response
from django.views.decorators.csrf import csrf_exempt
from django.utils.decorators import method_decorator
from django.core.files.base import ContentFile
from django.conf import settings
import base64
import json
import time
import os
import requests
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from .models import ErrorLog
from print_jobs.models import PrintJob
from .serializers import ErrorLogSerializer


class ErrorLogListView(generics.ListCreateAPIView):
    serializer_class = ErrorLogSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        return ErrorLog.objects.all()

class DetectionAlertView(views.APIView):
    """Receives defect alerts from the Pi and broadcasts them to the dashboard."""
    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    
    def post(self, request):
        error_type = request.data.get('error_type', 'unknown')
        confidence = request.data.get('confidence', 0.0)
        
        print(f"🚨 Received DEFECT ALERT from PI: {error_type} ({confidence})")
        
        channel_layer = get_channel_layer()
        async_to_sync(channel_layer.group_send)(
            'dashboard_updates',
            {
                'type': 'dashboard_message',
                'message_type': 'DETECTION_ALERT',
                'message': {
                    'error_type': error_type,
                    'confidence': confidence,
                    'action_taken': 'alerted'
                }
            }
        )
        return Response({'status': 'alert broadcasted'})


@method_decorator(csrf_exempt, name='dispatch')
class InferenceControlView(views.APIView):
    """Start or stop the YOLOv8 inference loop by writing/deleting the flag file."""
    permission_classes = [permissions.AllowAny]
    authentication_classes = []

    def _flag_path(self):
        return os.path.join(settings.MEDIA_ROOT, 'inference.flag')

    def get(self, request):
        """Return the current inference status."""
        flag_path = self._flag_path()
        running = os.path.exists(flag_path)
        active_job = PrintJob.objects.filter(status__in=['printing', 'paused']).first()
        return Response({
            'inference_running': running,
            'active_job_id': active_job.id if active_job else None,
            'active_job_status': active_job.status if active_job else None,
        })

    def post(self, request):
        """action=start → write flag; action=stop → delete flag."""
        action = request.data.get('action', 'start')
        flag_path = self._flag_path()
        os.makedirs(os.path.dirname(flag_path), exist_ok=True)

        if action == 'start':
            # Find an active or ready job to link to
            job = (
                PrintJob.objects.filter(status='printing').first()
                or PrintJob.objects.filter(status='ready_to_print').first()
            )
            if not job:
                # Promote any demo job to printing for the live demo
                demo_job = PrintJob.objects.filter(status='ready_to_print').first()
                if demo_job:
                    demo_job.status = 'printing'
                    demo_job.save(update_fields=['status'])
                    job = demo_job

            job_id = job.id if job else 'demo'
            with open(flag_path, 'w') as f:
                f.write(str(job_id))

            return Response({
                'inference_running': True,
                'message': f'Inference started (job={job_id}).',
            })

        elif action == 'stop':
            if os.path.exists(flag_path):
                os.remove(flag_path)
            return Response({
                'inference_running': False,
                'message': 'Inference stopped.',
            })

        return Response({'error': 'Invalid action. Use start or stop.'}, status=400)
