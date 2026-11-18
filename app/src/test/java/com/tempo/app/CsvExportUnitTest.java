package com.tempo.app;

import static org.junit.Assert.assertEquals;

import com.tempo.app.export.CsvExport;

import org.junit.Test;

public class CsvExportUnitTest {
    @Test public void plainCellsAreLeftAlone() {
        assertEquals("Essay draft", CsvExport.escape("Essay draft"));
        assertEquals("", CsvExport.escape(null));
    }

    @Test public void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"Read, then summarise\"", CsvExport.escape("Read, then summarise"));
        assertEquals("\"The \"\"big\"\" one\"", CsvExport.escape("The \"big\" one"));
        assertEquals("\"line 1\nline 2\"", CsvExport.escape("line 1\nline 2"));
    }

    @Test public void formulasCannotRunInASpreadsheet() {
        assertEquals("'=SUM(A1:A3)", CsvExport.escape("=SUM(A1:A3)"));
        assertEquals("'@cmd", CsvExport.escape("@cmd"));
    }
}
