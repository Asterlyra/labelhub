package com.labelhub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskTypeEnum {

    IMAGE_DETECT("IMAGE_DETECT", "图像目标检测"),
    IMAGE_CLASSIFY("IMAGE_CLASSIFY", "图像分类"),
    TEXT_CLASSIFY("TEXT_CLASSIFY", "文本分类"),
    NER("NER", "命名实体识别");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
