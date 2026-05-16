from rest_framework import serializers
from .models import ErrorLog


class ErrorLogSerializer(serializers.ModelSerializer):
    print_job_status = serializers.ReadOnlyField(source='print_job.status')

    class Meta:
        model = ErrorLog
        fields = [
            'id', 'print_job', 'print_job_status', 'error_type',
            'confidence', 'action_taken', 'frame_image', 'timestamp',
        ]
        read_only_fields = ['id', 'timestamp']
