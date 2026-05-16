from django.db import models
from print_jobs.models import PrintJob


class ErrorLog(models.Model):
    ERROR_TYPES = [
        ('spaghetti', 'Spaghetti'),
        ('layer_shift', 'Layer Shift'),
        ('nozzle_clog', 'Nozzle Clog'),
        ('adhesion_fail', 'Adhesion Failure'),
        ('stringing', 'Stringing'),
        ('other', 'Other'),
    ]
    ACTION_CHOICES = [
        ('paused', 'Print Paused'),
        ('alerted', 'Alert Sent'),
        ('continued', 'Continued'),
        ('cancelled', 'Print Cancelled'),
    ]

    print_job = models.ForeignKey(PrintJob, on_delete=models.CASCADE, related_name='error_logs')
    error_type = models.CharField(max_length=30, choices=ERROR_TYPES)
    confidence = models.FloatField(help_text='YOLOv8 confidence 0-1')
    action_taken = models.CharField(max_length=20, choices=ACTION_CHOICES, default='alerted')
    frame_image = models.ImageField(upload_to='detections/', blank=True, null=True)
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-timestamp']
        indexes = [models.Index(fields=['print_job', '-timestamp'])]

    def __str__(self):
        return f"{self.error_type} ({self.confidence:.0%}) at {self.timestamp:%H:%M}"
