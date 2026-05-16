from rest_framework import generics, permissions, status
from rest_framework.response import Response
from .models import GeneratedModel
from .serializers import GeneratedModelSerializer


class ModelListCreateView(generics.ListCreateAPIView):
    serializer_class = GeneratedModelSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        return GeneratedModel.objects.all()

    def perform_create(self, serializer):
        from django.contrib.auth.models import User
        user = self.request.user if self.request.user.is_authenticated else User.objects.first()
        serializer.save(user=user)


class ModelDetailView(generics.RetrieveUpdateDestroyAPIView):
    serializer_class = GeneratedModelSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        return GeneratedModel.objects.all()
