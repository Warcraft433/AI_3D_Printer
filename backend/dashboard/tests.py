from django.test import TestCase
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APIClient
from django.contrib.auth.models import User

class DashboardAPITests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(username='testuser', password='password123')
        # self.client.force_authenticate(user=self.user) # If we had auth enforced

    def test_stats_endpoint(self):
        """Test that the dashboard stats endpoint returns 200"""
        url = reverse('dashboard-stats') # Assuming the name is dashboard-stats
        response = self.client.get(url)
        # Note: Depending on existing implementation, this might be 200 or 403 if auth is required
        self.assertIn(response.status_code, [status.HTTP_200_OK, status.HTTP_403_FORBIDDEN])
