"""Agent服务的应用入口。"""

import logging
import os

from annotation_agent import create_app

logger = logging.getLogger(__name__)

# 创建Flask应用实例
app = create_app()

if __name__ == "__main__":
    debug = os.getenv("FLASK_DEBUG", "false").lower() == "true"
    host = os.getenv("FLASK_HOST", "0.0.0.0")
    port = int(os.getenv("FLASK_PORT", "5001"))
    logger.info("HTTP服务启动于 http://%s:%s", host, port)
    app.run(host=host, port=port, debug=debug)
