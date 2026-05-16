from rest_framework import serializers
from .models import PrintJob


class PrintJobSerializer(serializers.ModelSerializer):
    model_prompt = serializers.ReadOnlyField(source='model.prompt')
    model_status = serializers.ReadOnlyField(source='model.status')
    file_url = serializers.SerializerMethodField()
    gcode_url = serializers.SerializerMethodField()

    class Meta:
        model = PrintJob
        fields = [
            'id', 'model', 'model_prompt', 'model_status', 'file_url',
            'gcode_url', 'status', 'gcode_path', 'estimated_time', 'progress',
            'start_time', 'end_time', 'printer_ip', 'auto_pause_enabled', 'error_detail', 'created_at',
        ]
        read_only_fields = ['id', 'created_at', 'error_detail']

    def get_file_url(self, obj):
        request = self.context.get('request')
        if obj.model.file_path and request:
            return request.build_absolute_uri(f'/media/{obj.model.file_path}')
        return None

    def get_gcode_url(self, obj):
        request = self.context.get('request')
        if obj.gcode_path and request:
            return request.build_absolute_uri(f'/media/{obj.gcode_path}')
        return None
