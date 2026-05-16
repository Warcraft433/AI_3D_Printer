from rest_framework import serializers
from .models import GeneratedModel


class GeneratedModelSerializer(serializers.ModelSerializer):
    username = serializers.ReadOnlyField(source='user.username')
    file_url = serializers.SerializerMethodField()
    thumbnail_url = serializers.SerializerMethodField()

    class Meta:
        model = GeneratedModel
        fields = [
            'id', 'username', 'prompt', 'file_path', 'file_url',
            'thumbnail_path', 'thumbnail_url', 'generation_time',
            'status', 'error_detail', 'is_repaired', 'source_type', 'steps', 'created_at',
        ]
        read_only_fields = ['id', 'username', 'created_at', 'status', 'generation_time', 'error_detail']


    def get_file_url(self, obj):
        request = self.context.get('request')
        if obj.file_path and request:
            return request.build_absolute_uri(f'/media/{obj.file_path}')
        return None

    def get_thumbnail_url(self, obj):
        request = self.context.get('request')
        if obj.thumbnail_path and request:
            return request.build_absolute_uri(f'/media/{obj.thumbnail_path}')
        return None
