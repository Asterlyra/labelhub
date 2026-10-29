package com.labelhub.modules.dataset.service.impl.parser;

import com.labelhub.modules.dataset.enums.ParseStatusEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * XLSX 文件解析器，读取首个工作表并转换为数据项。
 */
@Component
public class ExcelFileParser implements FileParser {

    private static final String DEFAULT_SHEET_PATH = "xl/worksheets/sheet1.xml";

    @Override
    public ParseResult parse(InputStream inputStream) {
        try {
            Map<String, byte[]> entries = readZipEntries(inputStream);
            List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
            byte[] sheetBytes = entries.get(resolveFirstSheetPath(entries));
            if (sheetBytes == null) {
                throw new IllegalArgumentException("未找到 XLSX 工作表");
            }
            return parseSheet(sheetBytes, sharedStrings);
        } catch (Exception e) {
            throw new IllegalArgumentException("XLSX 文件解析失败：" + e.getMessage(), e);
        }
    }

    private Map<String, byte[]> readZipEntries(InputStream inputStream) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zipInputStream.readAllBytes());
                }
                zipInputStream.closeEntry();
            }
        }
        return entries;
    }

    private String resolveFirstSheetPath(Map<String, byte[]> entries) throws Exception {
        byte[] workbookBytes = entries.get("xl/workbook.xml");
        byte[] relsBytes = entries.get("xl/_rels/workbook.xml.rels");
        if (workbookBytes == null || relsBytes == null) {
            return DEFAULT_SHEET_PATH;
        }

        Document workbook = parseXml(workbookBytes);
        NodeList sheets = workbook.getElementsByTagNameNS("*", "sheet");
        if (sheets.getLength() == 0) {
            return DEFAULT_SHEET_PATH;
        }

        Element firstSheet = (Element) sheets.item(0);
        String relationId = firstSheet.getAttribute("r:id");
        if (!StringUtils.hasText(relationId)) {
            return DEFAULT_SHEET_PATH;
        }

        Document rels = parseXml(relsBytes);
        NodeList relationships = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            if (relationId.equals(relationship.getAttribute("Id"))) {
                return normalizeSheetTarget(relationship.getAttribute("Target"));
            }
        }
        return DEFAULT_SHEET_PATH;
    }

    private String normalizeSheetTarget(String target) {
        if (!StringUtils.hasText(target)) {
            return DEFAULT_SHEET_PATH;
        }
        String normalized = target.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        } else if (!normalized.startsWith("xl/")) {
            normalized = "xl/" + normalized;
        }
        return normalized.replace("xl/../", "");
    }

    private List<String> readSharedStrings(byte[] sharedStringBytes) throws Exception {
        List<String> sharedStrings = new ArrayList<>();
        if (sharedStringBytes == null) {
            return sharedStrings;
        }

        Document document = parseXml(sharedStringBytes);
        NodeList stringItems = document.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < stringItems.getLength(); i++) {
            sharedStrings.add(readText(stringItems.item(i)));
        }
        return sharedStrings;
    }

    private ParseResult parseSheet(byte[] sheetBytes, List<String> sharedStrings) throws Exception {
        Document document = parseXml(sheetBytes);
        NodeList rows = document.getElementsByTagNameNS("*", "row");
        ParseResult result = new ParseResult();
        List<String> headers = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.getLength(); rowIndex++) {
            Element row = (Element) rows.item(rowIndex);
            Map<Integer, String> cellValues = readRow(row, sharedStrings);
            if (cellValues.isEmpty()) {
                continue;
            }

            if (headers.isEmpty()) {
                headers = buildHeaders(cellValues);
                continue;
            }

            Map<String, Object> content = buildContent(headers, cellValues);
            if (content.isEmpty()) {
                continue;
            }
            result.addItem(toItem(content, readRowNumber(row, rowIndex + 1)));
        }
        return result;
    }

    private Map<Integer, String> readRow(Element row, List<String> sharedStrings) {
        Map<Integer, String> values = new LinkedHashMap<>();
        NodeList cells = row.getElementsByTagNameNS("*", "c");
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            int columnIndex = readColumnIndex(cell.getAttribute("r"), i);
            String value = readCellValue(cell, sharedStrings);
            if (StringUtils.hasText(value)) {
                values.put(columnIndex, value);
            }
        }
        return values;
    }

    private String readCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inlineStrings = cell.getElementsByTagNameNS("*", "is");
            return inlineStrings.getLength() == 0 ? "" : readText(inlineStrings.item(0));
        }

        NodeList values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() == 0) {
            return "";
        }
        String rawValue = values.item(0).getTextContent();
        if ("s".equals(type)) {
            int sharedIndex = Integer.parseInt(rawValue);
            return sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
        }
        if ("b".equals(type)) {
            return "1".equals(rawValue) ? "true" : "false";
        }
        return rawValue;
    }

    private List<String> buildHeaders(Map<Integer, String> cellValues) {
        int maxIndex = cellValues.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> headers = new ArrayList<>();
        for (int i = 0; i <= maxIndex; i++) {
            String header = cellValues.get(i);
            headers.add(StringUtils.hasText(header) ? header.trim() : "column" + (i + 1));
        }
        return headers;
    }

    private Map<String, Object> buildContent(List<String> headers, Map<Integer, String> cellValues) {
        Map<String, Object> content = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = cellValues.get(i);
            if (StringUtils.hasText(value)) {
                content.put(headers.get(i), value);
            }
        }
        return content;
    }

    private ParsedItem toItem(Map<String, Object> content, int rowNumber) {
        ParsedItem item = new ParsedItem();
        item.setItemKey(readItemKey(content, rowNumber));
        item.setRowIndex(rowNumber);
        item.setContent(content);
        item.setDataType(DataTypeDetector.detect(content));
        item.setParseStatus(ParseStatusEnum.SUCCESS.getCode());
        return item;
    }

    private String readItemKey(Map<String, Object> content, int rowNumber) {
        Object key = content.get("id");
        if (key == null) {
            key = content.get("key");
        }
        if (key == null) {
            key = content.get("itemKey");
        }
        return key == null ? String.valueOf(rowNumber) : String.valueOf(key);
    }

    private int readRowNumber(Element row, int fallback) {
        String rowNumber = row.getAttribute("r");
        if (!StringUtils.hasText(rowNumber)) {
            return fallback;
        }
        try {
            return Integer.parseInt(rowNumber);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int readColumnIndex(String cellRef, int fallback) {
        if (!StringUtils.hasText(cellRef)) {
            return fallback;
        }
        int result = 0;
        int letterCount = 0;
        for (char ch : cellRef.toCharArray()) {
            if (!Character.isLetter(ch)) {
                break;
            }
            result = result * 26 + (Character.toUpperCase(ch) - 'A' + 1);
            letterCount++;
        }
        return letterCount == 0 ? fallback : result - 1;
    }

    private String readText(Node node) {
        StringBuilder builder = new StringBuilder();
        NodeList texts = node instanceof Element element
                ? element.getElementsByTagNameNS("*", "t")
                : node.getChildNodes();
        for (int i = 0; i < texts.getLength(); i++) {
            Node textNode = texts.item(i);
            if ("t".equals(textNode.getLocalName()) || textNode.getNodeType() == Node.TEXT_NODE) {
                builder.append(textNode.getTextContent());
            }
        }
        return builder.toString();
    }

    private Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        String xml = new String(bytes, StandardCharsets.UTF_8);
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
