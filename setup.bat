@echo off
echo Starting AI-Integrated 3D Printer Setup...

:: Backend Setup
echo Setting up Backend...
cd backend
python -m venv venv
call venv\Scripts\activate
pip install -r requirements.txt
python manage.py migrate
cd ..

:: Frontend Setup
echo Setting up Frontend...
cd frontend
npm install
cd ..

echo Setup Complete! Use start.bat to run the project.
pause
