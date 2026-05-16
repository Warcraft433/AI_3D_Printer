# Troubleshooting Guide

Common issues and their solutions.

## Backend Issues

### "Database connection failed"
- **Solution:** Ensure PostgreSQL is running. Check your `.env` file for correct credentials. If using Docker, ensure the `db` service is healthy.

### "Celery worker not receiving tasks"
- **Solution:** Ensure Redis is running and the Celery worker is started.
  ```bash
  celery -A core worker --loglevel=info
  ```

## Frontend Issues

### "Cannot connect to backend"
- **Solution:** Check the `VITE_API_BASE_URL` in `frontend/.env`. Ensure the backend server is running on the specified port (usually 8000).

### "Webcam feed not showing"
- **Solution:** Ensure the OctoPrint webcam URL is correct and accessible from your browser. Cross-Origin Resource Sharing (CORS) might need to be configured in OctoPrint.

## AI & Hardware Issues

### "False Positives in defect detection"
- **Solution:** Adjust the confidence threshold in the dashboard settings. Improving lighting around the printer can also help.

### "Printer not responding to pause commands"
- **Solution:** Verify the OctoPrint API Key. Ensure the printer is in a state that allows pausing (i.e., currently printing).

## Docker Issues

### "Port already in use"
- **Solution:** Another service is using ports 3000 or 8000. Stop those services or change the port mapping in `docker-compose.yml`.
