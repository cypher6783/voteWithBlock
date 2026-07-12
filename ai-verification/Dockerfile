FROM python:3.10-slim
WORKDIR /app
COPY requirements.txt .

# Retry pip install up to 5 times to handle transient network drops
RUN for i in 1 2 3 4 5; do \
        echo "Attempt $i of 5..."; \
        pip install --default-timeout=300 -r requirements.txt && break; \
        echo "Attempt $i failed. Retrying in 10 seconds..."; \
        sleep 10; \
    done && \
    pip uninstall -y opencv-python opencv-python-headless && \
    pip install --default-timeout=300 --force-reinstall opencv-python-headless

COPY src/ /app/src/
EXPOSE 8000
CMD ["python", "src/main.py"]
