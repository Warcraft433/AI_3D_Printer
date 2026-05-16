
import os
import django
from django.conf import settings

# Setup Django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
django.setup()

from django.contrib.auth.models import User
from generated_models.models import GeneratedModel
from generated_models.tasks import run_shap_e_text_generation
from print_jobs.models import PrintJob
from print_jobs.tasks import run_slicing_task

def trigger_test():
    user = User.objects.get(username='admin')
    prompts = ["chess knight", "chess bishop"]
    
    for p in prompts:
        obj = GeneratedModel.objects.create(
            user=user,
            prompt=p,
            steps=32,
            source_type='text',
            status='processing'
        )
        print(f"Triggering generation for: {p} (Model ID: {obj.id})")
        task = run_shap_e_text_generation.delay(obj.id, p, 32)
        obj.celery_task_id = task.id
        obj.save()

if __name__ == "__main__":
    trigger_test()
