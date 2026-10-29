#!/bin/bash

set -u

# 定位到 Agent 根目录，保证双击运行时路径稳定。
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$AGENT_DIR" || exit 1

PYTHON_BIN="$AGENT_DIR/.venv/bin/python"
TIMESTAMP="$(date -u +"%Y%m%dT%H%M%SZ")"
SUFFIX="$(python3 - <<'PY'
import uuid
print(uuid.uuid4().hex[:6])
PY
)"
QUEUE_NAME="agent.pressure.smoke.${TIMESTAMP}.${SUFFIX}"
EXCHANGE_NAME="$QUEUE_NAME"
ROUTING_KEY="$QUEUE_NAME"
RUN_ID="smoke-${TIMESTAMP}-${SUFFIX}"
REPORT_DIR="$AGENT_DIR/tests/fixtures/generated/reports"
WORKER_LOG="$REPORT_DIR/${RUN_ID}-worker.log"
WORKER_PID=""

cleanup() {
  if [ -n "$WORKER_PID" ] && kill -0 "$WORKER_PID" >/dev/null 2>&1; then
    kill "$WORKER_PID" >/dev/null 2>&1 || true
    wait "$WORKER_PID" >/dev/null 2>&1 || true
  fi
}

pause_and_exit() {
  local exit_code="$1"
  echo
  read -r -p "按回车关闭窗口..." _
  exit "$exit_code"
}

trap cleanup EXIT

echo "== LabelHub Agent Smoke 压测 =="
echo "工作目录: $AGENT_DIR"
echo "运行 ID: $RUN_ID"
echo

if [ ! -x "$PYTHON_BIN" ]; then
  echo "未找到可执行 Python: $PYTHON_BIN"
  echo "请先在 Agent 目录准备好 .venv。"
  pause_and_exit 1
fi

mkdir -p "$REPORT_DIR"

echo "[1/4] 生成 MQ 测试载荷..."
if ! "$PYTHON_BIN" tests/tooling/build_dataset_mq_payloads.py; then
  echo "生成测试载荷失败。"
  pause_and_exit 1
fi

echo
echo "[2/4] 声明隔离 RabbitMQ 拓扑..."
if ! "$PYTHON_BIN" tests/tooling/run_mq_pressure_test.py \
  --exchange "$EXCHANGE_NAME" \
  --routing-key "$ROUTING_KEY" \
  --queue-name "$QUEUE_NAME" \
  --declare-topology-only; then
  echo "声明 RabbitMQ 拓扑失败。"
  pause_and_exit 1
fi

echo
echo "[3/4] 后台启动隔离 worker..."
AGENT_ENABLE_RABBITMQ_CONSUMER=true \
AI_AUDIT_USE_MOCK=true \
RABBITMQ_AI_REVIEW_QUEUE="$QUEUE_NAME" \
PYTHONUNBUFFERED=1 \
"$PYTHON_BIN" run_audit_worker.py >"$WORKER_LOG" 2>&1 &
WORKER_PID="$!"
sleep 2

if ! kill -0 "$WORKER_PID" >/dev/null 2>&1; then
  echo "worker 启动失败，日志如下："
  echo "----------------------------------------"
  cat "$WORKER_LOG"
  echo "----------------------------------------"
  pause_and_exit 1
fi

echo "worker 已启动，日志: $WORKER_LOG"

echo
echo "[4/4] 执行 smoke 压测（前 5 条消息）..."
if ! "$PYTHON_BIN" tests/tooling/run_mq_pressure_test.py \
  --exchange "$EXCHANGE_NAME" \
  --routing-key "$ROUTING_KEY" \
  --queue-name "$QUEUE_NAME" \
  --declare-topology \
  --limit 5 \
  --timeout 60 \
  --run-id "$RUN_ID"; then
  echo
  echo "压测执行失败，请检查以下内容："
  echo "- worker 日志: $WORKER_LOG"
  echo "- 压测报告目录: $REPORT_DIR"
  pause_and_exit 1
fi

echo
echo "Smoke 压测完成。"
echo "报告目录: $REPORT_DIR"
echo "worker 日志: $WORKER_LOG"
pause_and_exit 0
