# Use official high-performance Python slim image
FROM python:3.11-slim

# Set system-level environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Establish dynamic working directory
WORKDIR /app

# Install dependencies using cached layer
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy backend codebase to the working directory
COPY server.py .

# Expose default HTTP/WS port (Cloud Run defaults to 8080)
EXPOSE 8080

# Run FastAPI under Uvicorn, strictly routing to dynamic Cloud Run PORT or defaulting to 8080
CMD ["sh", "-c", "uvicorn server:app --host 0.0.0.0 --port ${PORT:-8080}"]
