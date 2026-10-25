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
        project_root = Path(__file__).resolve().parent.parent
        temp_out = project_root / "artifacts" / "test_cad" / "test_ai_king.obj"
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

    def test_text_to_3d_procedural_engine(self):
        project_root = Path(__file__).resolve().parent.parent
        temp_out = project_root / "artifacts" / "test_cad" / "test_text_to_3d_king.obj"
        temp_out.parent.mkdir(parents=True, exist_ok=True)

        req = {
            "prompt": "Staunton Chess King with crown",
            "guidance_scale": 15.0,
            "num_inference_steps": 32,
            "engine": "procedural",
            "format": "obj",
            "output_path": str(temp_out)
        }
        resp = self.client.post("/api/generate/text-to-3d", json=req)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data.get("success"))
        self.assertEqual(data.get("piece_name"), "Staunton Chess King")
        self.assertTrue(temp_out.is_file())
        self.assertGreater(temp_out.stat().st_size, 1000)

    def test_text_to_3d_shap_e_with_mocked_pipeline(self):
        import unittest.mock as mock
        import torch
        from types import SimpleNamespace
        import shap_e_service

        # Create a mock mesh with verts and faces
        mock_mesh = SimpleNamespace(
            verts=torch.tensor([
                [-1.0, -1.0, 0.0],
                [1.0, -1.0, 0.0],
                [1.0, 1.0, 0.0],
                [-1.0, 1.0, 0.0],
                [0.0, 0.0, 2.0]
            ], dtype=torch.float32),
            faces=torch.tensor([
                [0, 1, 4],
                [1, 2, 4],
                [2, 3, 4],
                [3, 0, 4],
                [0, 3, 2]
            ], dtype=torch.long)
        )
        mock_output = SimpleNamespace(images=[mock_mesh])
        mock_pipe = mock.MagicMock(return_value=mock_output)

        with mock.patch("shap_e_service.get_text_pipeline", return_value=mock_pipe):
            project_root = Path(__file__).resolve().parent.parent
            temp_out = project_root / "artifacts" / "test_cad" / "test_mock_text_3d.obj"
            req = {
                "prompt": "Neural Rocket Ship",
                "guidance_scale": 15.0,
                "num_inference_steps": 32,
                "engine": "shap-e",
                "format": "obj",
                "output_path": str(temp_out)
            }
            resp = self.client.post("/api/generate/text-to-3d", json=req)
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data.get("success"))
            self.assertEqual(data.get("generation_method"), "shap_e")
            self.assertFalse(data.get("is_fallback"))
            # Bed alignment check: min Z must be 0.0
            self.assertEqual(data.get("bounds")[0][2], 0.0)
            # Extents scaling check: max extent should match TARGET_EXTENT_MM
            max_dim = max(data.get("dimensions_mm"))
            self.assertAlmostEqual(max_dim, shap_e_service.TARGET_EXTENT_MM, delta=0.5)

    def test_text_to_3d_graceful_fallback_when_shap_e_disabled(self):
        import os
        os.environ["SHAP_E_ENABLED"] = "false"
        try:
            req = {
                "prompt": "Ceramic Mug",
                "engine": "shap-e",
                "format": "obj"
            }
            resp = self.client.post("/api/generate/text-to-3d", json=req)
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data.get("success"))
            self.assertTrue(data.get("is_fallback"))
            self.assertIn("FALLBACK", data.get("metadata_description", ""))
        finally:
            os.environ.pop("SHAP_E_ENABLED", None)

    def test_image_to_3d_multipart_upload(self):
        import io
        from PIL import Image

        # Create a small 32x32 test PNG in memory
        img = Image.new("RGBA", (32, 32), color=(255, 0, 0, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        buf.seek(0)

        project_root = Path(__file__).resolve().parent.parent
        temp_out = project_root / "artifacts" / "test_cad" / "test_img_to_3d.obj"

        resp = self.client.post(
            "/api/generate/image-to-3d",
            files={"image": ("test_gear.png", buf.getvalue(), "image/png")},
            data={"engine": "procedural", "name": "Mechanical Gear", "format": "obj", "output_path": str(temp_out)}
        )
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data.get("success"))
        self.assertTrue(temp_out.is_file())
        self.assertGreater(data.get("vertices"), 0)

    def test_image_to_3d_base64_json(self):
        import base64
        import io
        from PIL import Image

        img = Image.new("RGB", (16, 16), color=(0, 255, 0))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        b64_str = base64.b64encode(buf.getvalue()).decode("utf-8")

        req = {
            "image_base64": b64_str,
            "engine": "procedural",
            "name": "Coffee Cup",
            "format": "obj"
        }
        resp = self.client.post("/api/generate/image-to-3d", json=req)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data.get("success"))
        self.assertEqual(data.get("piece_name"), "Parametric Coffee Cup")

    def test_image_to_3d_with_mocked_pipeline(self):
        import unittest.mock as mock
        import torch
        import io
        from PIL import Image
        from types import SimpleNamespace

        mock_mesh = SimpleNamespace(
            verts=torch.tensor([
                [-2.0, -2.0, 0.0],
                [2.0, -2.0, 0.0],
                [2.0, 2.0, 0.0],
                [-2.0, 2.0, 0.0],
                [0.0, 0.0, 4.0]
            ], dtype=torch.float32),
            faces=torch.tensor([
                [0, 1, 4],
                [1, 2, 4],
                [2, 3, 4],
                [3, 0, 4],
                [0, 3, 2]
            ], dtype=torch.long)
        )
        mock_output = SimpleNamespace(images=[mock_mesh])
        mock_pipe = mock.MagicMock(return_value=mock_output)

        img = Image.new("RGB", (16, 16), color=(0, 0, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")

        with mock.patch("shap_e_service.get_image_pipeline", return_value=mock_pipe), \
             mock.patch("shap_e_service.preprocess_image_for_shap_e", side_effect=lambda im: im.convert("RGB").resize((256, 256))):
            resp = self.client.post(
                "/api/generate/image-to-3d",
                files={"file": ("blue_box.png", buf.getvalue(), "image/png")},
                data={"engine": "shap-e", "name": "Blue Box", "guidance_scale": "3.0", "num_inference_steps": "32"}
            )
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data.get("success"))
            self.assertEqual(data.get("generation_method"), "shap_e_vision")
            # Verify Z bed alignment
            self.assertEqual(data.get("bounds")[0][2], 0.0)

    def test_image_to_3d_rejects_empty_payload(self):
        resp = self.client.post("/api/generate/image-to-3d", json={})
        self.assertEqual(resp.status_code, 400)


if __name__ == "__main__":
    unittest.main()

