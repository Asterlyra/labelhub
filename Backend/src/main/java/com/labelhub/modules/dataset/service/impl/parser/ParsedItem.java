package com.labelhub.modules.dataset.service.impl.parser;

import lombok.Data;

import java.util.Map;

/**
 * 文件解析后的单条数据结果。
 */
@Data
public class ParsedItem {

    private String itemKey;

    private Integer rowIndex;

    private String dataType;

    private Map<String, Object> content;

    private String parseStatus;

    private String errorMessage;
}
