"""生产环境LLM网关服务。"""

from __future__ import annotations

import json
import logging
from typing import Any

from annotation_agent.app.settings import LLMSettings
from annotation_agent.application.ports import LLMGateway
from annotation_agent.infrastructure.llm.audit.output_local_repair import AuditOutputLocalRepair
from annotation_agent.infrastructure.llm.audit.mock_llm_client import MockLLMClient
from annotation_agent.infrastructure.llm.audit.output_validator import OutputValidator
from annotation_agent.infrastructure.llm.audit.schema import AUDIT_RESULT_SCHEMA
from annotation_agent.infrastructure.llm.shared.openai_compatible_client import OpenAICompatibleLLMClient
from annotation_agent.infrastructure.llm.shared.output_validation import OutputValidationError
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages
from annotation_agent.infrastructure.llm.suggestion.output_local_repair import SuggestionOutputLocalRepair
from annotation_agent.infrastructure.llm.suggestion.schema import SUGGESTION_RESULT_SCHEMA
from annotation_agent.infrastructure.prompts.prompt_template_repository import PromptTemplateRepository

logger = logging.getLogger(__name__)

# 兼容OpenAI接口的provider列表
OPENAI_COMPATIBLE_PROVIDERS = {"openai", "openai_compatible"}
# LLM调用最大重试次数
MAX_LLM_ATTEMPTS = 3


class LLMService(LLMGateway):
    """LLM网关服务，支持审核和建议生成，内置mock模式和重试逻辑。"""

    def __init__(self, settings: LLMSettings) -> None:
        """初始化LLM服务，创建校验器、模拟客户端和提示词模板仓储。"""
        self.settings = settings
        self.output_validator = OutputValidator()
        self.mock_client = MockLLMClient()
        self.prompt_template_repository = PromptTemplateRepository()
        self.audit_output_local_repair = AuditOutputLocalRepair()
        self.suggestion_output_local_repair = SuggestionOutputLocalRepair()

    def invoke_audit(
        self,
        prompt: str,
        dimensions: list[dict[str, Any]],
        audit_context: dict[str, Any] | None = None,
        prompt_messages: PromptMessages | None = None,
    ) -> dict[str, Any] | None:
        """执行审核LLM调用，支持mock模式和最多3次重试。"""
        # mock模式：使用本地模拟客户端
        if self._should_use_mock():
            model_result = self.mock_client.invoke_structured(prompt,
                                                              dimensions,
                                                              audit_context,
                                                              self._mock_reason())
            try:
                validated, _ = self._validate_with_local_repair(
                    model_result=model_result,
                    output_validator=self.output_validator,
                    validator_context=dimensions,
                    local_repair=self.audit_output_local_repair,
                    attempt=1,
                )
                return validated
            except OutputValidationError as exc:
                logger.warning("模拟审核输出校验失败: %s", exc)
                return None

        # 检查provider是否支持
        if self.settings.provider not in OPENAI_COMPATIBLE_PROVIDERS:
            logger.error("不支持的AI_AUDIT_PROVIDER: %s", self.settings.provider)
            return None

        # 创建OpenAI兼容客户端
        try:
            client = OpenAICompatibleLLMClient(
                model=self.settings.model_name,
                temperature=self.settings.temperature,
                api_key=self.settings.api_key or "",
                base_url=self.settings.base_url,
            )
        except RuntimeError as exc:
            logger.error("LLM客户端初始化失败: %s", exc)
            return None

        # 带重试的审核调用循环
        prompt_input: str | PromptMessages = prompt_messages or prompt
        errors: list[str] = []
        for _attempt in range(1, MAX_LLM_ATTEMPTS + 1):
            try:
                current_prompt = self._prompt_for_attempt(
                    prompt_input,
                    errors,
                    self._build_audit_retry_instruction(dimensions),
                )
                model_result = client.invoke_structured(
                    current_prompt,
                    AUDIT_RESULT_SCHEMA,
                    extra_instruction=self._build_audit_fallback_instruction(dimensions),
                )
                validated, _ = self._validate_with_local_repair(
                    model_result=model_result,
                    output_validator=self.output_validator,
                    validator_context=dimensions,
                    local_repair=self.audit_output_local_repair,
                    attempt=_attempt,
                )
                return validated
            except OutputValidationError as exc:
                errors.append(str(exc))
            except Exception as exc:  # pragma: no cover - network/provider failure path
                logger.exception("审核LLM调用尝试失败")
                errors.append(str(exc))
        return None

    def invoke_generation(
        self,
        prompt: str | PromptMessages,
        output_validator,
        validator_context: Any,
        llm_context: dict[str, Any] | None = None,
        mock_raw_output: str | None = None,
        structured_schema: dict[str, Any] | type[Any] | None = None,
    ) -> tuple[dict[str, Any] | None, str, dict[str, Any]]:
        """执行建议生成LLM调用，返回(校验结果, 原始输出, 调用追踪信息)。"""
        # LLM调用追踪信息
        llm_trace = {
            "attempts": 0,
            "errors": [],
            "success": False,
            "mode": "structured",
            "localRepairs": [],
        }

        # mock模式：使用本地模拟客户端
        if self._should_use_mock():
            if mock_raw_output:
                try:
                    model_result = json.loads(mock_raw_output)
                except json.JSONDecodeError:
                    model_result = self.mock_client.invoke_structured(prompt, [], llm_context, self._mock_reason())
            else:
                model_result = self.mock_client.invoke_structured(prompt, [], llm_context, self._mock_reason())
            try:
                validated, accepted_result = self._validate_with_local_repair(
                    model_result=model_result,
                    output_validator=output_validator,
                    validator_context=validator_context,
                    local_repair=self.suggestion_output_local_repair,
                    attempt=1,
                    llm_trace=llm_trace,
                )
            except OutputValidationError as exc:
                llm_trace["attempts"] = 1
                llm_trace["errors"].append(str(exc))
                return None, json.dumps(model_result, ensure_ascii=False), llm_trace
            llm_trace["attempts"] = 1
            llm_trace["success"] = True
            return validated, json.dumps(accepted_result, ensure_ascii=False), llm_trace

        # 检查provider是否支持
        if self.settings.provider not in OPENAI_COMPATIBLE_PROVIDERS:
            llm_trace["errors"].append(f"Unsupported AI_AUDIT_PROVIDER: {self.settings.provider}")
            return None, "", llm_trace

        # 创建OpenAI兼容客户端
        try:
            client = OpenAICompatibleLLMClient(
                model=self.settings.model_name,
                temperature=self.settings.temperature,
                api_key=self.settings.api_key or "",
                base_url=self.settings.base_url,
            )
        except RuntimeError as exc:
            llm_trace["errors"].append(str(exc))
            return None, "", llm_trace

        # 带重试的建议生成调用循环
        raw_output = ""
        for attempt in range(1, MAX_LLM_ATTEMPTS + 1):
            llm_trace["attempts"] = attempt
            try:
                current_prompt = self._prompt_for_attempt(prompt, llm_trace["errors"])
                model_result = client.invoke_structured(
                    current_prompt,
                    structured_schema or SUGGESTION_RESULT_SCHEMA,
                )
                validated, accepted_result = self._validate_with_local_repair(
                    model_result=model_result,
                    output_validator=output_validator,
                    validator_context=validator_context,
                    local_repair=self.suggestion_output_local_repair,
                    attempt=attempt,
                    llm_trace=llm_trace,
                )
                raw_output = json.dumps(accepted_result, ensure_ascii=False)
            except OutputValidationError as exc:
                llm_trace["errors"].append(str(exc))
                continue
            except Exception as exc:  # pragma: no cover - network/provider failure path
                logger.exception("建议LLM调用尝试失败")
                llm_trace["errors"].append(str(exc))
                continue
            llm_trace["success"] = True
            return validated, raw_output, llm_trace

        return None, raw_output, llm_trace

    def _validate_with_local_repair(
        self,
        model_result: dict[str, Any],
        output_validator,
        validator_context: Any,
        local_repair,
        attempt: int,
        llm_trace: dict[str, Any] | None = None,
    ) -> tuple[dict[str, Any], dict[str, Any]]:
        local_repair_result = local_repair.try_repair(model_result, validator_context) if local_repair is not None else None
        if local_repair_result is not None:
            try:
                validated = output_validator.validate(local_repair_result.repaired_result, validator_context)
            except OutputValidationError:
                validated = output_validator.validate(model_result, validator_context)
            else:
                self._record_local_repair(local_repair_result.actions, attempt, llm_trace)
                return validated, local_repair_result.repaired_result
        validated = output_validator.validate(model_result, validator_context)
        return validated, model_result

    @staticmethod
    def _record_local_repair(
        actions: list[str],
        attempt: int,
        llm_trace: dict[str, Any] | None,
    ) -> None:
        if not actions:
            return
        logger.info("LLM输出本地修正生效: attempt=%s, actions=%s", attempt, actions)
        if llm_trace is None:
            return
        llm_trace.setdefault("localRepairs", []).append(
            {
                "attempt": attempt,
                "actions": list(actions),
            }
        )

    def _should_use_mock(self) -> bool:
        """判断是否应使用mock模式（配置开启、provider为mock、或缺少API密钥）。"""
        return self.settings.use_mock or self.settings.provider == "mock" or not self.settings.api_key

    def _mock_reason(self) -> str:
        """返回使用mock模式的原因描述。"""
        if self.settings.use_mock or self.settings.provider == "mock":
            return "配置启用了模拟模式。"
        return "AI_AUDIT_API_KEY未配置，使用本地模拟模式。"

    def _prompt_for_attempt(
        self,
        prompt: str | PromptMessages,
        errors: list[str],
        extra_instruction: str = "",
    ) -> str | PromptMessages:
        """为重试尝试构建提示词，在原提示后附加错误修正指令。"""
        if not errors:
            return prompt

        # 加载重试提示词模板并替换错误信息
        retry_template = self.prompt_template_repository.get("retry_fix_json").content
        retry_instruction = f"\n\n{retry_template.replace('{error}', errors[-1])}"
        if extra_instruction:
            retry_instruction += extra_instruction

        # 根据提示类型追加重试指令
        if isinstance(prompt, PromptMessages):
            return PromptMessages(
                system_prompt=prompt.system_prompt,
                user_prompt=f"{prompt.user_prompt}{retry_instruction}",
                user_parts=prompt.user_parts,
            )
        return f"{prompt}{retry_instruction}"

    @staticmethod
    def _build_audit_fallback_instruction(dimensions: list[dict[str, Any]]) -> str:
        """构建审核回退指令，告知模型期望的JSON输出格式。"""
        rendered_dimensions = LLMService._render_dimension_fields(dimensions)
        return (
            "\n附加约束：\n"
            f"- scores必须恰好覆盖以下维度各一次：{rendered_dimensions}\n"
            "- 不要添加无关维度。\n"
            "- 请返回如下格式的JSON对象：\n"
            "{\n"
            '  "scores": [{"dimension": "字段名", "score": 0, "reason": "评分原因"}],\n'
            '  "summary": "总体评价",\n'
            '  "confidence": 0.0\n'
            "}\n"
        )

    @staticmethod
    def _build_audit_retry_instruction(dimensions: list[dict[str, Any]]) -> str:
        """构建审核重试指令，要求模型根据上次错误修正输出。"""
        return "\n\n请根据上次错误修正输出。\n" + LLMService._build_audit_fallback_instruction(dimensions)

    @staticmethod
    def _render_dimension_fields(dimensions: list[dict[str, Any]]) -> str:
        """将维度列表渲染为逗号分隔的字段名字符串。"""
        fields = [str(item.get("field", "")).strip() for item in dimensions if str(item.get("field", "")).strip()]
        return ", ".join(fields) if fields else "none"
