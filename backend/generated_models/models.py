from django.db import models
from django.contrib.auth.models import User


class GeneratedModel(models.Model):
    STATUS_CHOICES = [
        ('processing', 'Processing'),
        ('ready', 'Ready'),
        ('failed', 'Failed'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='models')
    prompt = models.TextField(blank=True)
    file_path = models.CharField(max_length=500, blank=True)
    thumbnail_path = models.CharField(max_length=500, blank=True)
    generation_time = models.FloatField(null=True, blank=True)  # seconds
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='processing')
    is_repaired = models.BooleanField(default=False)
    error_detail = models.TextField(blank=True)
    source_type = models.CharField(max_length=10, choices=[('text', 'Text'), ('image', 'Image')], default='text')
    steps = models.IntegerField(default=32)
    celery_task_id = models.CharField(max_length=255, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', '-created_at']),
            models.Index(fields=['status']),
        ]

    def __str__(self):
        return f"{self.user.username} – {self.prompt[:50]} ({self.status})"
