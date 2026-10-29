package com.labelhub.modules.dataset.enums;

import com.labelhub.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 数据集源文件格式枚举。
 */
@Getter
@AllArgsConstructor
public enum SourceFormatEnum {

    XLSX("xlsx"),
    CSV("csv"),
    JSON("json"),
    JSONL("jsonl");

    private final String code;

    /**
     * 根据文件名解析受支持的源文件格式。
     */
    public static SourceFormatEnum fromFilename(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new BusinessException("无法识别文件格式，仅支持 xlsx、csv、json、jsonl");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(extension))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的文件格式：" + extension));
    }

    /**
     * 根据格式编码解析枚举。
     */
    public static SourceFormatEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的文件格式：" + code));
    }
}
