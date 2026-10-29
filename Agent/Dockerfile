FROM python:3.11-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

ARG PIP_INDEX_URL=https://pypi.org/simple
ARG PIP_EXTRA_INDEX_URL
ARG PIP_TRUSTED_HOST
ARG PIP_DEFAULT_TIMEOUT=120
ARG PIP_RETRIES=10

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_DEFAULT_TIMEOUT=${PIP_DEFAULT_TIMEOUT}
ENV PIP_RETRIES=${PIP_RETRIES}

RUN if [ -n "${PIP_EXTRA_INDEX_URL}" ]; then pip config set global.extra-index-url "${PIP_EXTRA_INDEX_URL}"; fi \
    && if [ -n "${PIP_TRUSTED_HOST}" ]; then pip config set global.trusted-host "${PIP_TRUSTED_HOST}"; fi

COPY Agent/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY Agent/ ./

EXPOSE 5001

CMD ["python", "app.py"]
