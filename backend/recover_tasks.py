import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
django.setup()

from generated_models.models import GeneratedModel
from print_jobs.models import PrintJob
from generated_models.tasks import run_shap_e_text_generation
from print_jobs.tasks import run_slicing_task

print("Checking for stranded models...")
for m in GeneratedModel.objects.filter(status='processing', celery_task_id=''):
    print(f"Re-triggering Model {m.id}: {m.prompt}")
    t = run_shap_e_text_generation.delay(m.pk, m.prompt, m.steps)
    m.celery_task_id = t.id
    m.save(update_fields=['celery_task_id'])

print("Checking for stranded print jobs...")
for j in PrintJob.objects.filter(status='queued', celery_task_id=''):
    print(f"Re-triggering Job {j.id}")
    t = run_slicing_task.delay(j.pk)
    j.celery_task_id = t.id
    j.save(update_fields=['celery_task_id'])

print("Recovery complete.")
