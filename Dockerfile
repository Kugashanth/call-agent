FROM python:3.11-slim-bookworm
# Azure Speech SDK native deps
RUN apt-get update && apt-get install -y --no-install-recommends libssl3 libasound2 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
# run as an unprivileged user; only audio/ and calls/ are writable
RUN useradd --create-home --uid 10001 agent && mkdir -p audio calls recordings && chown -R agent:agent audio calls recordings
USER agent
CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8000} --proxy-headers"]
