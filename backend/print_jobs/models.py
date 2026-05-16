from django.db import models
from django.conf import settings
from generated_models.models import GeneratedModel


class PrintJob(models.Model):
    STATUS_CHOICES = [
        ('queued', 'Queued'),
        ('slicing', 'Slicing'),
        ('ready_to_print', 'Ready to Print'),
        ('printing', 'Printing'),
        ('paused', 'Paused'),
        ('completed', 'Completed'),
        ('failed', 'Failed'),
        ('cancelled', 'Cancelled'),
    ]

    model = models.ForeignKey(GeneratedModel, on_delete=models.CASCADE, related_name='print_jobs')
    celery_task_id = models.CharField(max_length=255, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='queued')
    gcode_path = models.CharField(max_length=500, blank=True)
    estimated_time = models.IntegerField(null=True, blank=True, help_text='Seconds')
    progress = models.IntegerField(default=0, help_text='0-100%')
    start_time = models.DateTimeField(null=True, blank=True)
    end_time = models.DateTimeField(null=True, blank=True)
    printer_ip = models.GenericIPAddressField(null=True, blank=True)
    auto_pause_enabled = models.BooleanField(default=True, help_text='Pause print automatically on detection')
    error_detail = models.TextField(blank=True, default='')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']
        indexes = [models.Index(fields=['status'])]

    def __str__(self):
        return f"PrintJob #{self.pk} – {self.status}"


class AuditLog(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='audit_logs')
    action = models.CharField(max_length=255)
    job_id = models.IntegerField(null=True, blank=True)
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-timestamp']

    def __str__(self):
        return f"{self.user.username} - {self.action} at {self.timestamp}"
