package com.labelhub.modules.dataset.service.impl.parser;

import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.dataset.enums.SourceFormatEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * 根据源文件格式选择解析器。
 */
@Component
@RequiredArgsConstructor
public class FileParserFactory {

    private final ExcelFileParser excelFileParser;
    private final CsvFileParser csvFileParser;
    private final JsonFileParser jsonFileParser;

    /**
     * 获取指定格式的解析器。
     */
    public FileParser getParser(String format) {
        SourceFormatEnum formatEnum = SourceFormatEnum.fromCode(format);
        return switch (formatEnum) {
            case XLSX -> excelFileParser;
            case CSV -> csvFileParser;
            case JSON, JSONL -> jsonFileParser;
        };
    }

    /**
     * 执行文件解析。
     */
    public ParseResult parse(String format, InputStream inputStream) {
        SourceFormatEnum formatEnum = SourceFormatEnum.fromCode(format);
        try {
            if (formatEnum == SourceFormatEnum.JSONL) {
                return jsonFileParser.parseJsonLines(inputStream);
            }
            return getParser(format).parse(inputStream);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 分批执行文件解析。
     */
    public void parseBatches(String format, InputStream inputStream, int batchSize,
                             Consumer<List<ParsedItem>> batchConsumer) {
        SourceFormatEnum formatEnum = SourceFormatEnum.fromCode(format);
        try {
            if (formatEnum == SourceFormatEnum.JSONL) {
                jsonFileParser.parseJsonLinesBatches(inputStream, batchSize, batchConsumer);
                return;
            }
            getParser(format).parseBatches(inputStream, batchSize, batchConsumer);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件解析失败：" + e.getMessage());
        }
    }
}
