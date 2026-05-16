import os
import uuid
from django.conf import settings
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from generated_models.models import GeneratedModel
from generated_models.serializers import GeneratedModelSerializer
from generated_models.tasks import run_shap_e_text_generation, run_shap_e_image_generation


class TextGenerateView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        prompt = request.data.get('prompt', '').strip()
        steps = int(request.data.get('steps', 32))
        if not prompt:
            return Response({'error': 'Prompt required'}, status=status.HTTP_400_BAD_REQUEST)

        obj = GeneratedModel.objects.create(
            user=user,
            prompt=prompt,
            steps=steps,
            source_type='text',
            status='processing',
        )

        task = run_shap_e_text_generation.delay(obj.pk, prompt, steps)
        obj.celery_task_id = task.id
        obj.save(update_fields=['celery_task_id'])

        return Response(
            GeneratedModelSerializer(obj, context={'request': request}).data,
            status=status.HTTP_202_ACCEPTED,
        )


class ImageGenerateView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        image = request.FILES.get('image')
        steps = int(request.data.get('steps', 32))
        if not image:
            return Response({'error': 'Image required'}, status=status.HTTP_400_BAD_REQUEST)

        from django.contrib.auth.models import User
        user = request.user if request.user.is_authenticated else User.objects.first()
        upload_dir = os.path.join(settings.MEDIA_ROOT, 'uploads', str(user.id))
        os.makedirs(upload_dir, exist_ok=True)
        img_filename = f"{uuid.uuid4().hex}_{image.name}"
        img_path = os.path.join(upload_dir, img_filename)
        with open(img_path, 'wb') as f:
            for chunk in image.chunks():
                f.write(chunk)

        obj = GeneratedModel.objects.create(
            user=user,
            prompt=f'[Image] {image.name}',
            steps=steps,
            source_type='image',
            status='processing',
        )

        task = run_shap_e_image_generation.delay(obj.pk, img_path, steps)
        obj.celery_task_id = task.id
        obj.save(update_fields=['celery_task_id'])

        return Response(
            GeneratedModelSerializer(obj, context={'request': request}).data,
            status=status.HTTP_202_ACCEPTED,
        )


class ShapeModelStatusView(APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        python = getattr(settings, 'SHAPE_VENV_PYTHON', '')
        available = bool(python and os.path.isfile(python))
        return Response({'available': available, 'python': python})
