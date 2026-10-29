"""把仓库 datasets 样例批量生成成后端风格的 MQ 审核消息。"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]
DEFAULT_DATASETS_ROOT = BASE_DIR.parent / "docs" / "datasets"
DEFAULT_OUTPUT_DIR = BASE_DIR / "tests" / "fixtures" / "generated"

if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

from annotation_agent.tooling.dataset_mq_payload_builder import build_dataset_bundle, write_dataset_bundle


def build_parser() -> argparse.ArgumentParser:
    """定义脚本命令行参数。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--datasets-root",
        type=Path,
        default=DEFAULT_DATASETS_ROOT,
        help=f"datasets root directory (default: {DEFAULT_DATASETS_ROOT})",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"output directory (default: {DEFAULT_OUTPUT_DIR})",
    )
    return parser


def main() -> int:
    """执行数据集到 MQ 载荷的生成流程。"""
    args = build_parser().parse_args()
    bundle = build_dataset_bundle(args.datasets_root)
    manifest = write_dataset_bundle(bundle, args.output_dir)
    counts = manifest["counts"]
    print(
        "generated MQ payload fixtures: "
        f"qa_quality={counts['qa_quality']}, "
        f"preference_compare={counts['preference_compare']}, "
        f"all={counts['all']}"
    )
    print(f"output dir: {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
