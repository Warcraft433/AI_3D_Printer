import os
from celery import Celery

# Set the default Django settings module for the 'celery' program.
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')

app = Celery('core')

# Using a string here means the worker doesn't have to serialize
# the configuration object to child processes.
# - namespace='CELERY' means all celery-related configuration keys
#   should have a `CELERY_` prefix.
app.config_from_object('django.conf:settings', namespace='CELERY')

# Load task modules from all registered Django apps.
app.autodiscover_tasks()

# Celery Beat schedule for background polling tasks
app.conf.beat_schedule = {
    'check-active-prints-every-5-seconds': {
        'task': 'print_jobs.tasks.check_active_print_jobs',
        'schedule': 5.0,  # Run every 5 seconds
    },
    'cleanup-old-files-hourly': {
        'task': 'print_jobs.tasks.cleanup_old_files',
        'schedule': 3600.0, # Run every hour (3600s)
    }
}

@app.task(bind=True, ignore_result=True)
def debug_task(self):
    print(f'Request: {self.request!r}')
