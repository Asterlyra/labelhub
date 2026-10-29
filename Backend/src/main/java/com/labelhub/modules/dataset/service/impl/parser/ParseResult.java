package com.labelhub.modules.dataset.service.impl.parser;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件解析汇总结果。
 */
@Data
public class ParseResult {

    private final List<ParsedItem> items = new ArrayList<>();

    /**
     * 添加一条解析结果。
     */
    public void addItem(ParsedItem item) {
        items.add(item);
    }
}
