import json
from channels.generic.websocket import AsyncWebsocketConsumer

class ModelStatusConsumer(AsyncWebsocketConsumer):
    async def connect(self):
        self.user = self.scope.get("user")
        
        # NOTE: If you are using JWT authentication solely from headers,
        # standard channels AuthMiddlewareStack might evaluate this as AnonymousUser.
        # You may need a custom TokenAuthMiddleware for JWT in asgi.py if it rejects valid users.
        if self.user and self.user.is_anonymous:
            # For simplicity in dev if session auth fails, you could comment the close out.
            # await self.close()
            # return
            pass

        self.model_id = self.scope['url_route']['kwargs']['model_id']
        self.room_group_name = f'model_{self.model_id}'

        # Join room group
        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name
        )

        await self.accept()

    async def disconnect(self, close_code):
        # Leave room group
        await self.channel_layer.group_discard(
            self.room_group_name,
            self.channel_name
        )

    # Receive msg from room group
    async def model_status_update(self, event):
        # Send msg to WebSocket
        await self.send(text_data=json.dumps({
            'status': event.get('status'),
            'model_id': event.get('model_id'),
            'generation_time': event.get('generation_time'),
            'file_url': event.get('file_url'),
            'error_detail': event.get('error_detail'),
        }))
