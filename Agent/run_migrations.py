"""Agent MySQL migrations entrypoint."""

from __future__ import annotations

import logging
import sys

from annotation_agent.app.container import ServiceContainer


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(threadName)s] %(levelname)s %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        stream=sys.stdout,
    )
    logging.getLogger("pika").setLevel(logging.WARNING)


def main() -> int:
    _configure_logging()
    container = ServiceContainer()

    logging.getLogger(__name__).info(
        "Starting Agent MySQL migrations for database '%s'.",
        container.settings.mysql.database,
    )
    container.run_migrations()
    logging.getLogger(__name__).info("Agent MySQL migrations completed successfully.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
