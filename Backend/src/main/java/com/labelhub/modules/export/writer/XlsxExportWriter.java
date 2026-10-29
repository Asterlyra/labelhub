package com.labelhub.modules.export.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.enums.ExportFormatEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@RequiredArgsConstructor
public class XlsxExportWriter implements ExportWriter {

    private final ObjectMapper objectMapper;

    @Override
    public ExportFormatEnum format() {
        return ExportFormatEnum.XLSX;
    }

    @Override
    public byte[] write(List<Map<String, Object>> rows, List<ExportFieldMappingDTO> fields) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            addEntry(zip, "[Content_Types].xml", contentTypesXml());
            addEntry(zip, "_rels/.rels", relsXml());
            addEntry(zip, "xl/workbook.xml", workbookXml());
            addEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            addEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(rows, fields));
            zip.finish();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Excel 导出失败：" + e.getMessage());
        }
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n"
                + "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
                + "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n"
                + "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n"
                + "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                + "</Types>\n";
    }

    private String relsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
                + "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n"
                + "</Relationships>\n";
    }

    private String workbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n"
                + "  <sheets>\n"
                + "    <sheet name=\"export\" sheetId=\"1\" r:id=\"rId1\"/>\n"
                + "  </sheets>\n"
                + "</workbook>\n";
    }

    private String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
                + "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n"
                + "</Relationships>\n";
    }

    private String worksheetXml(List<Map<String, Object>> rows, List<ExportFieldMappingDTO> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
                .append("  <sheetData>\n");
        appendRow(builder, 1, fields.stream().map(ExportFieldMappingDTO::getTargetName).toList());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            appendRow(builder, i + 2, fields.stream()
                    .map(field -> valueToText(row.get(field.getTargetName())))
                    .toList());
        }
        builder.append("""
                  </sheetData>
                </worksheet>
                """);
        return builder.toString();
    }

    private void appendRow(StringBuilder builder, int rowIndex, List<String> values) {
        builder.append("    <row r=\"").append(rowIndex).append("\">");
        for (int i = 0; i < values.size(); i++) {
            String cellReference = columnName(i + 1) + rowIndex;
            builder.append("<c r=\"").append(cellReference).append("\" t=\"inlineStr\"><is><t>")
                    .append(escapeXml(values.get(i)))
                    .append("</t></is></c>");
        }
        builder.append("</row>\n");
    }

    private String columnName(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index;
        while (value > 0) {
            value--;
            builder.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return builder.toString();
    }

    private String valueToText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String escapeXml(String value) {
        String text = value == null ? "" : value;
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
