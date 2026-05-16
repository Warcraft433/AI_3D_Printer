import json
from channels.generic.websocket import AsyncWebsocketConsumer

class PrintStatusConsumer(AsyncWebsocketConsumer):
    async def connect(self):
        self.user = self.scope.get("user")
        
        if self.user and self.user.is_anonymous:
            pass

        self.job_id = self.scope['url_route']['kwargs']['job_id']
        self.room_group_name = f'print_job_{self.job_id}'

        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name
        )

        await self.accept()

    async def disconnect(self, close_code):
        await self.channel_layer.group_discard(
            self.room_group_name,
            self.channel_name
        )

    async def print_status_update(self, event):
        await self.send(text_data=json.dumps({
            'status': event.get('status'),
            'job_id': event.get('job_id'),
            'progress': event.get('progress'),
            'temperatures': event.get('temperatures'),
            'error_detail': event.get('error_detail'),
        }))
