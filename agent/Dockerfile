FROM python:3.11-slim
WORKDIR /opt/careermate-agent
COPY vendor /tmp/vendor
COPY requirements.lock /opt/careermate-agent/requirements.lock
RUN pip install --no-cache-dir /tmp/vendor/keel_sdk-*.whl -r /opt/careermate-agent/requirements.lock && rm -rf /tmp/vendor
COPY . /opt/careermate-agent
RUN mkdir -p /opt/careermate-agent/.keel && useradd --uid 10001 --no-create-home agent && chown -R 10001:10001 /opt/careermate-agent
USER 10001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
