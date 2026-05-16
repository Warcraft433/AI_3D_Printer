#!/bin/bash

echo "Starting AI-Integrated 3D Printer Setup..."

# Backend Setup
echo "Setting up Backend..."
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
cd ..

# Frontend Setup
echo "Setting up Frontend..."
cd frontend
npm install
cd ..

echo "Setup Complete! Use ./start.sh to run the project."
