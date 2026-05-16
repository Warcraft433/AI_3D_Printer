# AI Model Training & Inference

This project uses YOLOv8 for real-time defect detection during the 3D printing process.

## Supported Defects

Our model is trained to detect:
- **Spaghetti Failure:** Tangled filaments caused by bed adhesion failure.
- **Warping:** Corners of the print lifting off the bed.
- **Stringing:** Thin wisps of plastic between parts.
- **Under-extrusion:** Missing layers or gaps.
- **Layer Shifting:** Misaligned layers.

## Training the Model

If you wish to retrain or fine-tune the model:

1. **Dataset Preparation:**
   Collect images of both successful and failed prints. Annotate them using tools like [Roboflow](https://roboflow.com).

2. **Setup Training Environment:**
   ```bash
   pip install ultralytics
   ```

3. **Run Training:**
   ```python
   from ultralytics import YOLO

   model = YOLO('yolov8n.pt')  # Load a pretrained model
   results = model.train(data='custom_dataset.yaml', epochs=100, imgsz=640)
   ```

4. **Export Weights:**
   The best weights will be saved in `runs/detect/train/weights/best.pt`. Copy this file to the `ai-model/` directory of this project.

## Inference Pipeline

The monitoring service fetches the webcam feed from OctoPrint, runs it through the YOLOv8 model, and if a defect is detected with a confidence above the threshold, it triggers an alert via the Django backend.
