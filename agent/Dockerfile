FROM python:3.11-slim
WORKDIR /app
COPY vendor /tmp/vendor
COPY requirements.lock /app/requirements.lock
RUN pip install --no-cache-dir /tmp/vendor/keel_sdk-*.whl -r /app/requirements.lock && rm -rf /tmp/vendor
COPY . /app
RUN mkdir -p /app/.keel && useradd --uid 10001 --no-create-home agent && chown -R 10001:10001 /app
USER 10001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
