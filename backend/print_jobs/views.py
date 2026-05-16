import os
from django.conf import settings
from django.http import FileResponse, Http404
from rest_framework import generics, permissions, status, views
from rest_framework.response import Response
from .models import PrintJob, AuditLog
from .serializers import PrintJobSerializer
from .tasks import run_slicing_task, start_print_task


class PrintJobListCreateView(generics.ListCreateAPIView):
    serializer_class = PrintJobSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        from django.contrib.auth.models import User
        user = self.request.user if self.request.user.is_authenticated else User.objects.first()
        return PrintJob.objects.filter(model__user=user)

    def post(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        job = serializer.save()

        # Trigger Celery Task
        task = run_slicing_task.delay(job.pk)
        job.celery_task_id = task.id
        job.save(update_fields=['celery_task_id'])

        headers = self.get_success_headers(serializer.data)
        return Response(serializer.data, status=status.HTTP_201_CREATED, headers=headers)


class PrintJobDetailView(generics.RetrieveUpdateDestroyAPIView):
    serializer_class = PrintJobSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        from django.contrib.auth.models import User
        user = self.request.user if self.request.user.is_authenticated else User.objects.first()
        return PrintJob.objects.filter(model__user=user)


class DownloadGCodeView(views.APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request, pk, *args, **kwargs):
        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        try:
            job = PrintJob.objects.get(pk=pk, model__user=user)
        except PrintJob.DoesNotExist:
            raise Http404("PrintJob not found.")

        if not job.gcode_path:
            raise Http404("G-Code not generated yet.")

        # Prevent directory traversal by using basename
        filename = os.path.basename(job.gcode_path)
        
        # We know gcode_path might be things like 'slicing/abc.gcode' but just to be safe
        file_path = os.path.normpath(os.path.join(settings.MEDIA_ROOT, job.gcode_path))
        
        # Security: ensure file_path is strictly within MEDIA_ROOT
        if not file_path.startswith(os.path.normpath(settings.MEDIA_ROOT)):
            return Response({'error': 'Invalid path'}, status=status.HTTP_400_BAD_REQUEST)

        if not os.path.exists(file_path):
            raise Http404("File missing on disk.")

        # Log audit
        AuditLog.objects.create(
            user=user,
            action=f"Downloaded G-code for PrintJob #{job.id} ({filename})",
            job_id=job.id
        )

        response = FileResponse(open(file_path, 'rb'), as_attachment=True, filename=filename)
        return response


class StartPrintView(views.APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request, pk, *args, **kwargs):
        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        try:
            job = PrintJob.objects.get(pk=pk, model__user=user)
        except PrintJob.DoesNotExist:
            return Response({'error': 'PrintJob not found.'}, status=status.HTTP_404_NOT_FOUND)

        if job.status != 'ready_to_print':
            return Response({'error': 'Job is not ready to print.'}, status=status.HTTP_400_BAD_REQUEST)

        job.status = 'printing'
        job.save(update_fields=['status'])

        # Trigger Celery Task
        task = start_print_task.delay(job.pk)
        job.celery_task_id = task.id
        job.save(update_fields=['celery_task_id'])

        # Write inference flag so run_inference management command begins processing immediately
        flag_path = os.path.join(settings.MEDIA_ROOT, 'inference.flag')
        try:
            with open(flag_path, 'w') as f:
                f.write(str(job.pk))
        except Exception as e:
            print(f"Warning: Could not write inference flag: {e}")

        return Response({'status': 'printing', 'job_id': job.pk}, status=status.HTTP_200_OK)


class PauseResumeView(views.APIView):
    """POST /api/print-jobs/<id>/resume/ — Resume an AI-paused print."""
    permission_classes = [permissions.AllowAny]

    def post(self, request, pk, *args, **kwargs):
        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        try:
            job = PrintJob.objects.get(pk=pk, model__user=user)
        except PrintJob.DoesNotExist:
            return Response({'error': 'PrintJob not found.'}, status=status.HTTP_404_NOT_FOUND)

        # Send Resume to OctoPrint
        octo_url = getattr(settings, 'OCTOPRINT_URL', 'http://localhost:5000')
        octo_key = getattr(settings, 'OCTOPRINT_API_KEY', '')
        
        headers = {'X-Api-Key': octo_key}
        payload = {"command": "pause", "action": "resume"} # OctoPrint uses pause/resume toggle on the same endpoint
        
        try:
            r = requests.post(f"{octo_url.rstrip('/')}/api/job", json=payload, headers=headers, timeout=5)
            if r.ok:
                job.status = 'printing'
                job.save(update_fields=['status'])
                return Response({'status': 'resumed'})
            else:
                return Response({'error': f'Printer rejected resume: {r.text}'}, status=400)
        except Exception as e:
            return Response({'error': f'Connection failed: {str(e)}'}, status=500)


class ClearFinishedJobsView(views.APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request, *args, **kwargs):
        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        finished_statuses = ['completed', 'failed', 'cancelled']
        deleted_count, _ = PrintJob.objects.filter(
            model__user=user, 
            status__in=finished_statuses
        ).delete()
        
        return Response({
            'message': f'Successfully cleared {deleted_count} jobs.',
            'count': deleted_count
        }, status=status.HTTP_200_OK)


class ActivePrintJobView(views.APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request, *args, **kwargs):
        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        # Get the most recent job that is in a 'printing' or 'ready_to_print' state
        active_job = PrintJob.objects.filter(
            model__user=user,
            status__in=['printing', 'ready_to_print']
        ).first()

        if not active_job:
            return Response({'detail': 'No active print job found.'}, status=status.HTTP_404_NOT_FOUND)

        serializer = PrintJobSerializer(active_job, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)
