"""兼容 OpenAI 接口的 LLM 客户端。"""

from __future__ import annotations

import hashlib
import inspect
import json
import logging
from typing import Any, Type, Union

from langchain_core.runnables import RunnableLambda

from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages

logger = logging.getLogger(__name__)

StructuredSchema = Union[dict[str, Any], Type[Any]]
PromptInput = Union[str, PromptMessages]

# 记录 provider 对结构化输出和多模态输入的支持情况，避免重复探测。
_structured_output_support_cache: dict[str, bool] = {}
_multimodal_input_support_cache: dict[str, bool] = {}
TRANSPORT_RETRY_ATTEMPTS = 3


def reset_structured_output_support_cache() -> None:
    """清空 provider 能力探测缓存。"""
    _structured_output_support_cache.clear()
    _multimodal_input_support_cache.clear()


class OpenAICompatibleLLMClient:
    """对 ChatOpenAI 做一层统一包装，处理结构化输出与降级逻辑。"""

    def __init__(self, model: str, temperature: float, api_key: str, base_url: str | None = None) -> None:
        try:
            from langchain_openai import ChatOpenAI
        except ImportError as exc:
            raise RuntimeError("Missing langchain-openai dependency. Run pip install -r requirements.txt") from exc

        llm_kwargs: dict[str, Any] = {
            "model": model,
            "temperature": temperature,
            "api_key": api_key,
        }
        if base_url:
            llm_kwargs["base_url"] = base_url

        self.model = model
        self.base_url = base_url or ""
        self.llm = ChatOpenAI(**llm_kwargs)
        self._structured_llm_cache: dict[str, Any] = {}

    def invoke(self, prompt: PromptInput) -> str:
        """调用 LLM 并返回文本内容。"""
        try:
            response = self._invoke_with_transport_retry(
                self.llm,
                prompt,
                retry_if=self._is_retryable_transport_error,
            )
        except Exception as exc:
            if self._should_retry_without_multimodal(prompt, exc):
                _multimodal_input_support_cache[self._provider_key()] = False
                response = self._invoke_with_transport_retry(
                    self.llm,
                    prompt,
                    retry_if=self._is_retryable_transport_error,
                )
            else:
                raise
        return getattr(response, "content", str(response))

    def invoke_structured(
        self,
        prompt: PromptInput,
        schema: StructuredSchema,
        extra_instruction: str = "",
    ) -> dict[str, Any]:
        """优先走结构化输出，失败时自动退回 JSON 指令模式。"""
        provider_key = self._provider_key()
        if self._should_force_json_instruction_mode():
            _structured_output_support_cache[provider_key] = False
            return self._invoke_with_json_instruction(prompt, schema, extra_instruction)
        if _structured_output_support_cache.get(provider_key) is False:
            return self._invoke_with_json_instruction(prompt, schema, extra_instruction)

        try:
            structured_llm = self._get_structured_llm(schema)
            try:
                raw_result = self._invoke_with_transport_retry(
                    structured_llm,
                    prompt,
                    retry_if=self._is_retryable_structured_error,
                )
            except Exception as exc:
                if self._should_retry_without_multimodal(prompt, exc):
                    _multimodal_input_support_cache[provider_key] = False
                    raw_result = self._invoke_with_transport_retry(
                        structured_llm,
                        prompt,
                        retry_if=self._is_retryable_structured_error,
                    )
                else:
                    raise
            parsed = self._coerce_json_object(raw_result)
            _structured_output_support_cache[provider_key] = True
            return parsed
        except Exception as exc:
            if self._is_structured_output_unsupported(exc):
                _structured_output_support_cache[provider_key] = False
            logger.warning("结构化输出请求失败，回退到 JSON 模式: %s", exc)
            return self._invoke_with_json_instruction(prompt, schema, extra_instruction)

    def _get_structured_llm(self, schema: StructuredSchema) -> Any:
        """获取带结构化输出能力的 LLM 实例，并做 schema 级缓存。"""
        schema_key = self._schema_cache_key(schema)
        structured_llm = self._structured_llm_cache.get(schema_key)
        if structured_llm is None:
            structured_llm = self.llm.with_structured_output(schema, method="json_schema", strict=True)
            self._structured_llm_cache[schema_key] = structured_llm
        return structured_llm

    def _invoke_with_transport_retry(
        self,
        runnable: Any,
        prompt: PromptInput,
        *,
        retry_if,
    ) -> Any:
        normalized_prompt = self._normalize_prompt_input(prompt)

        def _call(_unused: object) -> Any:
            try:
                return runnable.invoke(normalized_prompt)
            except Exception as exc:
                if retry_if(exc):
                    raise _RetryableTransportError(str(exc)) from exc
                raise

        retried = RunnableLambda(_call).with_retry(
            retry_if_exception_type=(_RetryableTransportError,),
            stop_after_attempt=TRANSPORT_RETRY_ATTEMPTS,
        )
        try:
            return retried.invoke(None)
        except _RetryableTransportError as exc:  # pragma: no cover - unwrap only after retry exhaustion
            if exc.__cause__ is not None:
                raise exc.__cause__
            raise

    @staticmethod
    def _schema_cache_key(schema: StructuredSchema) -> str:
        """为 schema 生成稳定缓存键。"""
        if isinstance(schema, dict):
            raw_schema = json.dumps(schema, ensure_ascii=True, sort_keys=True)
        elif inspect.isclass(schema):
            raw_schema = f"{schema.__module__}.{schema.__qualname__}"
        else:
            raw_schema = str(schema)
        return hashlib.sha1(raw_schema.encode("utf-8")).hexdigest()

    def _provider_key(self) -> str:
        """用 base_url 和 model 组合出 provider 唯一标识。"""
        return f"{self.base_url}|{self.model}"

    def _should_force_json_instruction_mode(self) -> bool:
        """对已知兼容性较差的接口直接禁用严格结构化输出。"""
        normalized_base_url = self.base_url.strip().lower()
        if not normalized_base_url:
            return False
        return "dashscope.aliyuncs.com/compatible-mode" in normalized_base_url

    def _invoke_with_json_instruction(
        self,
        prompt: PromptInput,
        schema: StructuredSchema,
        extra_instruction: str = "",
    ) -> dict[str, Any]:
        """当 provider 不支持结构化输出时，退化为纯文本 JSON 约束。"""
        schema_description = self._schema_to_description(schema)
        json_instruction = (
            "\n\n请仅返回一个合法的JSON对象，不要使用Markdown代码块。\n"
            "期望的JSON结构：\n"
            f"{schema_description}\n"
        )
        if extra_instruction:
            json_instruction += extra_instruction
        raw_output = self.invoke(self._append_user_instruction(prompt, json_instruction))
        return self._parse_json_from_text(raw_output)

    def _normalize_prompt_input(self, prompt: PromptInput) -> str | list[tuple[str, str]] | list[dict[str, Any]]:
        """把提示输入统一转换为模型可接受的格式。"""
        if isinstance(prompt, PromptMessages):
            if _multimodal_input_support_cache.get(self._provider_key()) is False:
                return prompt.as_text_only_chat_messages()
            return prompt.as_chat_messages()
        return prompt

    @staticmethod
    def _append_user_instruction(prompt: PromptInput, instruction: str) -> PromptInput:
        """把额外指令追加到用户提示词末尾。"""
        if isinstance(prompt, PromptMessages):
            return PromptMessages(
                system_prompt=prompt.system_prompt,
                user_prompt=f"{prompt.user_prompt}{instruction}",
                user_parts=prompt.user_parts,
            )
        return prompt + instruction

    def _should_retry_without_multimodal(self, prompt: PromptInput, exc: Exception) -> bool:
        """判断是否需要禁用多模态消息后再重试一次。"""
        return isinstance(prompt, PromptMessages) and bool(prompt.user_parts) and self._is_multimodal_input_unsupported(exc)

    def _is_retryable_transport_error(self, exc: Exception) -> bool:
        if self._is_multimodal_input_unsupported(exc):
            return False
        if self._is_schema_error(exc):
            return False
        return not isinstance(exc, (json.JSONDecodeError, ValueError, TypeError))

    def _is_retryable_structured_error(self, exc: Exception) -> bool:
        if self._is_structured_output_unsupported(exc):
            return False
        return self._is_retryable_transport_error(exc)

    @staticmethod
    def _schema_to_description(schema: StructuredSchema) -> str:
        """把 schema 转成更适合放进提示词的人类可读描述。"""
        schema_dict = OpenAICompatibleLLMClient._schema_to_dict(schema)
        lines: list[str] = []
        properties = schema_dict.get("properties", {})
        required_fields = set(schema_dict.get("required", []))
        for name, prop in properties.items():
            type_description = OpenAICompatibleLLMClient._describe_schema_node(prop)
            requirement = "required" if name in required_fields else "optional"
            description = str(prop.get("description", "")).strip()
            line = f'  "{name}": {type_description} ({requirement})'
            if description:
                line += f" - {description}"
            lines.append(line)
        return "{\n" + "\n".join(lines) + "\n}"

    @staticmethod
    def _schema_to_dict(schema: StructuredSchema) -> dict[str, Any]:
        """把 dict 或 Pydantic 模型统一转成 schema 字典。"""
        if isinstance(schema, dict):
            return schema
        if inspect.isclass(schema) and hasattr(schema, "model_json_schema"):
            return schema.model_json_schema()
        raise TypeError(f"Unsupported structured schema type: {type(schema)!r}")

    @staticmethod
    def _describe_schema_node(node: dict[str, Any]) -> str:
        """递归描述 schema 节点类型。"""
        if "anyOf" in node:
            variants = [
                OpenAICompatibleLLMClient._describe_schema_node(variant)
                for variant in node["anyOf"]
                if isinstance(variant, dict)
            ]
            return " or ".join(variants) if variants else "value"
        node_type = node.get("type", "value")
        if node_type == "array":
            return f"array of {OpenAICompatibleLLMClient._describe_schema_node(node.get('items', {}))}"
        if node_type == "object":
            nested_properties = node.get("properties", {})
            return "object {" + ", ".join(nested_properties.keys()) + "}" if nested_properties else "object"
        return str(node_type)

    @staticmethod
    def _parse_json_from_text(text: Any) -> dict[str, Any]:
        """从模型文本输出中提取 JSON 对象。"""
        if isinstance(text, dict):
            return text
        if hasattr(text, "model_dump") and callable(text.model_dump):
            dumped = text.model_dump(mode="json")
            if isinstance(dumped, dict):
                return dumped
        if isinstance(text, bytes):
            text = text.decode("utf-8")
        if not isinstance(text, str):
            text = str(text)

        try:
            return json.loads(text.strip())
        except json.JSONDecodeError:
            pass

        if "```json" in text:
            start = text.find("```json") + 7
            end = text.find("```", start)
            if end > start:
                try:
                    return json.loads(text[start:end].strip())
                except json.JSONDecodeError:
                    pass

        cleaned = "\n".join(
            line
            for line in text.splitlines()
            if not line.strip().startswith("|") and not line.strip().startswith("---")
        )
        target = cleaned if cleaned.strip() else text
        start = target.find("{")
        end = target.rfind("}") + 1
        if start >= 0 and end > start:
            try:
                return json.loads(target[start:end])
            except json.JSONDecodeError:
                pass

        raise ValueError(f"Unable to parse JSON object from model output: {text[:200]}...")

    @staticmethod
    def _coerce_json_object(content: Any) -> dict[str, Any]:
        """把结构化输出结果强制转换为字典。"""
        if isinstance(content, dict):
            return content
        if hasattr(content, "model_dump") and callable(content.model_dump):
            dumped = content.model_dump(mode="json")
            if isinstance(dumped, dict):
                return dumped
        if isinstance(content, bytes):
            content = content.decode("utf-8")
        if not isinstance(content, str):
            content = str(content)
        parsed = json.loads(content)
        if not isinstance(parsed, dict):
            raise ValueError("Structured output must be a JSON object")
        return parsed

    @staticmethod
    def _is_structured_output_unsupported(exc: Exception) -> bool:
        """判断异常是否意味着 provider 不支持结构化输出。"""
        message = str(exc).lower()
        if OpenAICompatibleLLMClient._is_schema_error(exc):
            return False

        unsupported_markers = (
            "response_format",
            "structured output",
            "json_schema is not supported",
            "json schema is not supported",
            "not support",
            "not supported",
            "unsupported",
            "unknown parameter",
        )
        return any(marker in message for marker in unsupported_markers)

    @staticmethod
    def _is_multimodal_input_unsupported(exc: Exception) -> bool:
        """判断异常是否意味着 provider 不支持 image_url 等多模态块。"""
        message = str(exc).lower()
        has_image_url_marker = "image_url" in message
        has_variant_mismatch_marker = (
            "unknown variant" in message
            or "expected `text`" in message
            or "expected 'text'" in message
            or "deserialize" in message
        )
        return has_image_url_marker and has_variant_mismatch_marker

    @staticmethod
    def _is_schema_error(exc: Exception) -> bool:
        message = str(exc).lower()
        schema_error_markers = (
            "invalid schema",
            "schema validation",
            "failed to validate schema",
            "does not conform",
            "properties",
            "required",
            "anyof",
            "allof",
            "additionalproperties",
            "top-level 'title' key",
            "top-level \"title\" key",
        )
        return any(marker in message for marker in schema_error_markers)


class _RetryableTransportError(Exception):
    """Internal wrapper used to scope LangChain retry to transport failures only."""
