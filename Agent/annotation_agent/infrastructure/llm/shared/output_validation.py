"""LLM 输出校验相关的共享类型。"""


class OutputValidationError(ValueError):
    """当模型输出不满足预期结构时抛出。"""
