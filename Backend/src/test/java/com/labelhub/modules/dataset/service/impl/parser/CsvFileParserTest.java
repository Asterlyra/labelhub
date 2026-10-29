package com.labelhub.modules.dataset.service.impl.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvFileParserTest {

    private final CsvFileParser parser = new CsvFileParser();

    @Test
    void parseSupportsBomQuotesAndMultilineFields() {
        String csv = "\uFEFFid,name,note\n"
                + "1,Alice,\"hello, csv\"\n"
                + "2,Bob,\"line1\n"
                + "line2\"\n"
                + "3,Carol,\"say \"\"yes\"\"\"\n";

        ParseResult result = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, result.getItems().size());
        assertEquals("1", result.getItems().get(0).getItemKey());
        assertEquals(2, result.getItems().get(0).getRowIndex());
        assertEquals("hello, csv", result.getItems().get(0).getContent().get("note"));
        assertEquals("line1\nline2", result.getItems().get(1).getContent().get("note"));
        assertEquals("say \"yes\"", result.getItems().get(2).getContent().get("note"));
    }
}
