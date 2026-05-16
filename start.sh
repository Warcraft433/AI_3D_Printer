#!/bin/bash

echo "Starting AI-Integrated 3D Printer..."

# Run Backend
echo "Starting Backend..."
cd backend
source venv/bin/activate
python manage.py runserver & 
BACKEND_PID=$!
cd ..

# Run Frontend
echo "Starting Frontend..."
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

echo "Project is running!"
echo "Backend PID: $BACKEND_PID"
echo "Frontend PID: $FRONTEND_PID"

trap "kill $BACKEND_PID $FRONTEND_PID; exit" INT TERM EXIT
wait
