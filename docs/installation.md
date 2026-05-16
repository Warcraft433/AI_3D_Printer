# Installation Guide

This guide will help you set up the AI-Integrated 3D Printer project on your local machine.

## Prerequisites

- **Python 3.10+**
- **Node.js 18+**
- **Docker & Docker Compose** (Recommended for easiest setup)
- **OctoPrint** (Running on a Raspberry Pi or local server)
- **Git**

## Option 1: Using Docker (Recommended)

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/ai-3d-printer.git
   cd ai-3d-printer
   ```

2. **Set up environment variables:**
   Copy `.env.example` to `.env` in both `backend/` and `frontend/` directories and fill in the details.

3. **Run with Docker Compose:**
   ```bash
   docker-compose up --build
   ```

4. **Access the application:**
   - Frontend: `http://localhost:3000`
   - Backend API: `http://localhost:8000`

## Option 2: Local Setup (Manual)

### Backend (Django)

1. **Navigate to backend folder:**
   ```bash
   cd backend
   ```

2. **Create and activate a virtual environment:**
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Run migrations:**
   ```bash
   python manage.py migrate
   ```

5. **Start the server:**
   ```bash
   python manage.py runserver
   ```

### Frontend (React)

1. **Navigate to frontend folder:**
   ```bash
   cd frontend
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Start the development server:**
   ```bash
   npm run dev
   ```

## Setup OctoPrint Connection

To connect the system to your printer:
1. Open the dashboard.
2. Go to **Settings**.
3. Enter your **OctoPrint IP Address** and **API Key**.
4. Click **Test Connection**.
