FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && pip list
COPY . .
CMD ["sh", "-c", "python -m uvicorn main:app --host 0.0.0.0 --port $PORT"]