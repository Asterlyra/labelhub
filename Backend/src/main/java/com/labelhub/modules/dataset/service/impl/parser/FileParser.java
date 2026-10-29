package com.labelhub.modules.dataset.service.impl.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 数据集文件解析器接口。
 */
public interface FileParser {

    /**
     * 将源文件解析为统一的数据项集合。
     */
    ParseResult parse(InputStream inputStream);

    /**
     * 分批解析数据项。默认实现兼容旧解析器：先全量解析，再按批次回调。
     */
    default void parseBatches(InputStream inputStream, int batchSize, Consumer<List<ParsedItem>> batchConsumer) {
        ParseResult result = parse(inputStream);
        List<ParsedItem> items = result.getItems();
        for (int start = 0; start < items.size(); start += batchSize) {
            int end = Math.min(start + batchSize, items.size());
            batchConsumer.accept(new ArrayList<>(items.subList(start, end)));
        }
    }
}
