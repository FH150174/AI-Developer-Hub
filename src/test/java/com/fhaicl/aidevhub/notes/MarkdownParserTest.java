package com.fhaicl.aidevhub.notes;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownParserTest {
    @Test
    void parsesHeadingsParagraphsAndWindowsNewlines() {
        var blocks = MarkdownParser.parse("# 标题\r\n\r\n中文 paragraph\r\nsecond line\r\n");
        assertEquals(2, blocks.size());
        assertEquals(MarkdownParser.Kind.HEADING, blocks.getFirst().kind());
        assertEquals(1, blocks.getFirst().level());
        assertEquals("中文 paragraph\nsecond line", blocks.getLast().spans().getFirst().text());
    }

    @Test
    void rendersBasicInlineStylesAndLeavesSourceUnchanged() {
        String source = "normal **bold** *italic* `**code**` __strong__ _em_";
        var spans = MarkdownParser.inline(source);
        assertEquals(List.of("bold", "italic", "**code**", "strong", "em"),
                spans.stream().filter(span -> span.style() != MarkdownParser.Style.NORMAL)
                        .map(MarkdownParser.Span::text).toList());
        assertEquals(MarkdownParser.Style.CODE,
                spans.stream().filter(span -> span.text().equals("**code**")).findFirst().orElseThrow().style());
        assertEquals("normal **bold** *italic* `**code**` __strong__ _em_", source);
    }

    @Test
    void parsesListsAndQuotes() {
        var blocks = MarkdownParser.parse("- one\n+ two\n* three\n1. ordered\n> quoted");
        assertEquals(5, blocks.size());
        assertEquals("•", blocks.getFirst().marker());
        assertEquals("1.", blocks.get(3).marker());
        assertEquals(MarkdownParser.Kind.QUOTE, blocks.getLast().kind());
    }

    @Test
    void preservesCodeWhitespaceAndDoesNotParseInlineOrHeadingsInsideFence() {
        var blocks = MarkdownParser.parse("```java\n  # literal\n**literal**\n\n```\n\nafter");
        assertEquals(MarkdownParser.Kind.CODE, blocks.getFirst().kind());
        assertEquals("  # literal\n**literal**\n\n", blocks.getFirst().spans().getFirst().text());
        assertEquals(MarkdownParser.Style.CODE, blocks.getFirst().spans().getFirst().style());
        assertEquals("after", blocks.getLast().spans().getFirst().text());
    }

    @Test
    void handlesUnclosedFenceAndUnmatchedInlineMarkers() {
        assertEquals("code\n", MarkdownParser.parse("```\ncode").getFirst().spans().getFirst().text());
        assertEquals(List.of(new MarkdownParser.Span("plain **unfinished", MarkdownParser.Style.NORMAL)),
                MarkdownParser.inline("plain **unfinished"));
    }

    @Test
    void keepsHtmlAndUnsupportedLinkSyntaxAsLiteralText() {
        String source = "<script>alert(1)</script> [link](https://example.com) ![image](file:///secret)";
        assertEquals(source, MarkdownParser.parse(source).getFirst().spans().getFirst().text());
    }

    @Test
    void handlesEmptyInputAndReturnsImmutableResults() {
        assertTrue(MarkdownParser.parse("").isEmpty());
        assertTrue(MarkdownParser.parse("  \n\n").isEmpty());
        var blocks = MarkdownParser.parse("# title");
        assertThrows(UnsupportedOperationException.class, blocks::clear);
        assertThrows(UnsupportedOperationException.class, () -> blocks.getFirst().spans().clear());
    }
}
