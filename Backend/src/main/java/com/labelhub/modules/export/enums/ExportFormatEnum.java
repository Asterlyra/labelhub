package com.labelhub.modules.export.enums;

import com.labelhub.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 导出格式枚举。
 */
@Getter
@AllArgsConstructor
public enum ExportFormatEnum {

    JSON("json", "application/json;charset=UTF-8", ".json"),
    JSONL("jsonl", "application/x-ndjson;charset=UTF-8", ".jsonl"),
    CSV("csv", "text/csv;charset=UTF-8", ".csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");

    private final String code;
    private final String contentType;
    private final String extension;

    public static ExportFormatEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的导出格式：" + code));
    }
}
