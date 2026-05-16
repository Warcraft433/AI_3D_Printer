FROM node:18-slim

WORKDIR /app

# Install dependencies
COPY frontend/package*.json ./
RUN npm install

# Copy frontend code
COPY frontend/ .

# Expose port
EXPOSE 3000

# Start command
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
