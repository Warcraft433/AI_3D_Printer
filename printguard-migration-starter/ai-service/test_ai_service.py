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

if __name__ == "__main__":
    unittest.main()
