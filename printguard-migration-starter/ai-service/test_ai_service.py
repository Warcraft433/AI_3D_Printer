"""
Unit tests for PrintGuard AI microservice (FastAPI + YOLO / Procedural CAD).
"""
import unittest
from pathlib import Path
from fastapi.testclient import TestClient
from app import app

class AIServiceTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data.get("service"), "printguard-ai")
        self.assertIn("status", data)

    def test_inference_health_endpoint(self):
        response = self.client.get("/api/inference/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data.get("service"), "printguard-ai")

    def test_procedural_geometry_generation(self):
        temp_out = Path("c:/Projects01/AI_3D_Printer/artifacts/test_cad/test_ai_king.obj")
        temp_out.parent.mkdir(parents=True, exist_ok=True)

        req = {
            "prompt": "Chess King with royal finial",
            "output_path": str(temp_out),
            "format": "obj"
        }
        response = self.client.post("/api/geometry/generate", json=req)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data.get("success"))
        self.assertTrue(temp_out.is_file())
        self.assertGreater(temp_out.stat().st_size, 10000)

    def test_detect_frame_rejects_empty(self):
        response = self.client.post(
            "/api/inference/detect-frame",
            files={"file": ("empty.jpg", b"", "image/jpeg")}
        )
        self.assertEqual(response.status_code, 400)

    def test_unauthorized_when_token_configured_and_missing(self):
        import os
        os.environ["AI_SERVICE_API_KEY"] = "super-secret-test-key-2026"
        try:
            # Missing token header on detect-frame
            resp = self.client.post(
                "/api/inference/detect-frame",
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 401)
            self.assertIn("Unauthorized", resp.json().get("detail", ""))

            # Missing token header on geometry generation
            resp_geo = self.client.post("/api/geometry/generate", json={"prompt": "king"})
            self.assertEqual(resp_geo.status_code, 401)
        finally:
            os.environ.pop("AI_SERVICE_API_KEY", None)

    def test_unauthorized_when_token_configured_and_invalid(self):
        import os
        os.environ["AI_SERVICE_API_KEY"] = "super-secret-test-key-2026"
        try:
            resp = self.client.post(
                "/api/inference/detect-frame",
                headers={"X-Internal-Token": "wrong-token"},
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 401)

            resp_bearer = self.client.post(
                "/api/inference/detect-frame",
                headers={"Authorization": "Bearer wrong-token"},
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp_bearer.status_code, 401)
        finally:
            os.environ.pop("AI_SERVICE_API_KEY", None)

    def test_authorized_when_valid_x_internal_token_provided(self):
        import os
        os.environ["AI_SERVICE_API_KEY"] = "super-secret-test-key-2026"
        try:
            # Valid token passes auth check; reaches empty file validation (400 Bad Request, not 401)
            resp = self.client.post(
                "/api/inference/detect-frame",
                headers={"X-Internal-Token": "super-secret-test-key-2026"},
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 400)
            self.assertIn("empty", resp.json().get("detail", ""))
        finally:
            os.environ.pop("AI_SERVICE_API_KEY", None)

    def test_authorized_when_valid_bearer_token_provided(self):
        import os
        os.environ["AI_SERVICE_API_KEY"] = "super-secret-test-key-2026"
        try:
            resp = self.client.post(
                "/api/inference/detect-frame",
                headers={"Authorization": "Bearer super-secret-test-key-2026"},
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 400)
            self.assertIn("empty", resp.json().get("detail", ""))
        finally:
            os.environ.pop("AI_SERVICE_API_KEY", None)

    def test_health_endpoints_remain_public_when_token_configured(self):
        import os
        os.environ["AI_SERVICE_API_KEY"] = "super-secret-test-key-2026"
        try:
            resp = self.client.get("/health")
            self.assertEqual(resp.status_code, 200)

            resp_inf = self.client.get("/api/inference/health")
            self.assertEqual(resp_inf.status_code, 200)
        finally:
            os.environ.pop("AI_SERVICE_API_KEY", None)

    def test_production_environment_rejects_requests_when_token_is_unset(self):
        import os
        os.environ["ENVIRONMENT"] = "production"
        os.environ.pop("AI_SERVICE_API_KEY", None)
        try:
            resp = self.client.post(
                "/api/inference/detect-frame",
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 500)
            self.assertIn("Security configuration error", resp.json().get("detail", ""))

            # Health probe reflects degraded state in production without token
            resp_health = self.client.get("/health")
            self.assertEqual(resp_health.status_code, 200)
            self.assertEqual(resp_health.json().get("status"), "DEGRADED")
            self.assertFalse(resp_health.json().get("security_configured"))
        finally:
            os.environ.pop("ENVIRONMENT", None)

    def test_render_environment_rejects_requests_when_token_is_unset(self):
        import os
        os.environ["RENDER"] = "true"
        os.environ.pop("AI_SERVICE_API_KEY", None)
        try:
            resp = self.client.post(
                "/api/inference/detect-frame",
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 500)
            self.assertIn("Security configuration error", resp.json().get("detail", ""))
        finally:
            os.environ.pop("RENDER", None)

    def test_production_environment_accepts_requests_when_token_is_configured_and_valid(self):
        import os
        os.environ["ENVIRONMENT"] = "production"
        os.environ["AI_SERVICE_API_KEY"] = "prod-secret-key-xyz"
        try:
            resp = self.client.post(
                "/api/inference/detect-frame",
                headers={"X-Internal-Token": "prod-secret-key-xyz"},
                files={"file": ("empty.jpg", b"", "image/jpeg")}
            )
            self.assertEqual(resp.status_code, 400) # Passes auth, fails on empty file
            self.assertIn("empty", resp.json().get("detail", ""))

            # Health probe reflects UP state when token is configured
            resp_health = self.client.get("/health")
            self.assertEqual(resp_health.status_code, 200)
            self.assertEqual(resp_health.json().get("status"), "UP")
            self.assertTrue(resp_health.json().get("security_configured"))
        finally:
            os.environ.pop("ENVIRONMENT", None)
            os.environ.pop("AI_SERVICE_API_KEY", None)


if __name__ == "__main__":
    unittest.main()
