#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
ENV_FILE="$ROOT_DIR/deploy/docker.env"
EXAMPLE_FILE="$ROOT_DIR/deploy/docker.env.example"

if [ ! -f "$ENV_FILE" ]; then
  cp "$EXAMPLE_FILE" "$ENV_FILE"
  echo "Created $ENV_FILE from template. Review it before the next deployment if needed."
fi

cd "$ROOT_DIR"
docker compose --env-file "$ENV_FILE" build
docker compose --profile migrate --env-file "$ENV_FILE" run --rm agent-migrate
docker compose --env-file "$ENV_FILE" up -d
