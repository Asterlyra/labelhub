"""标注模式解析与归一化工具。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class SchemaField:
    """标注模式中的单个字段定义。"""
    field: str  # 字段标识
    label: str  # 字段显示名称
    type: str  # 字段类型（text、select、multiSelect 等）
    required: bool = False  # 是否必填
    options: list[Any] = field(default_factory=list)  # 可选项列表
    config: dict[str, Any] = field(default_factory=dict)  # 字段附加配置

    def to_dict(self) -> dict[str, Any]:
        """转换为字典表示。"""
        return {
            "field": self.field,
            "label": self.label,
            "type": self.type,
            "required": self.required,
            "options": self.options,
            "config": self.config,
        }


def normalize_annotation_schema(annotation_schema: dict[str, Any] | None) -> dict[str, Any]:
    """对标注模式做归一化处理，统一 multiple / type 等字段。"""
    if not isinstance(annotation_schema, dict):
        return {}

    normalized_schema = dict(annotation_schema)
    components = annotation_schema.get("components", [])
    if not isinstance(components, list):
        normalized_schema["components"] = []
        return normalized_schema

    # 逐个归一化组件
    normalized_schema["components"] = [
        _normalize_component(component) if isinstance(component, dict) else component
        for component in components
    ]
    return normalized_schema


class AnnotationSchemaParser:
    """将原始标注模式解析为 SchemaField 列表。"""

    def parse(self,
              annotation_schema: dict[str, Any] | None) -> list[SchemaField]:
        """解析标注模式，返回字段列表。"""
        normalized_schema = normalize_annotation_schema(annotation_schema)
        if not normalized_schema:
            return []

        components = normalized_schema.get("components", [])
        if not isinstance(components, list):
            return []

        parsed_fields: list[SchemaField] = []
        for component in components:
            if not isinstance(component, dict):
                continue

            field_name = str(component.get("field") or "").strip()
            if not field_name:
                continue

            parsed_fields.append(
                SchemaField(
                    field=field_name,
                    label=str(component.get("label") or field_name),
                    type=str(component.get("type") or "text"),
                    required=bool(component.get("required", False)),
                    options=self._extract_options(component),
                    config=self._extract_config(component),
                )
            )
        return parsed_fields

    @staticmethod
    def _extract_options(component: dict[str, Any]) -> list[Any]:
        """提取组件中的可选项列表。"""
        options = component.get("options")
        if not isinstance(options, list):
            return []
        return options

    @staticmethod
    def _extract_config(component: dict[str, Any]) -> dict[str, Any]:
        """提取组件中的附加配置。"""
        config = component.get("config")
        if isinstance(config, dict):
            return dict(config)
        return {}


def _normalize_component(component: dict[str, Any]) -> dict[str, Any]:
    """归一化单个组件，同步 multiple 属性并推导最终类型。"""
    normalized_component = dict(component)
    config = component.get("config")
    normalized_config = dict(config) if isinstance(config, dict) else {}

    # 读取并统一 multiple 标志
    multiple = _read_component_multiple(component, normalized_config)
    if "multiple" in component or "multiple" in normalized_config:
        normalized_component["multiple"] = multiple
        normalized_config["multiple"] = multiple

    normalized_component["config"] = normalized_config
    # 根据 multiple 推导最终类型（select / multiSelect）
    normalized_component["type"] = _normalize_component_type(component, multiple)
    return normalized_component


def _normalize_component_type(component: dict[str, Any], multiple: bool) -> str:
    """根据组件类型和 multiple 标志返回归一化后的类型字符串。"""
    component_type = str(component.get("type") or "text").strip()
    if component_type in {"tagSelect", "select"}:
        return "multiSelect" if multiple else "select"
    return component_type


def _read_component_multiple(component: dict[str, Any], config: dict[str, Any]) -> bool:
    """读取组件或配置中的 multiple 布尔值。"""
    if "multiple" in component:
        return _to_bool(component.get("multiple"))
    return _to_bool(config.get("multiple"))


def _to_bool(value: Any) -> bool:
    """将任意值转换为布尔类型。"""
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    return str(value).lower() in {"1", "true", "yes"}
