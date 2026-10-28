package com.fhaicl.aidevhub.notes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** A deliberately small Markdown subset; unsupported syntax remains literal text. */
public final class MarkdownParser {
    public enum Kind { PARAGRAPH, HEADING, LIST_ITEM, QUOTE, CODE }
    public enum Style { NORMAL, BOLD, ITALIC, CODE }
    public record Span(String text, Style style) { }
    public record Block(Kind kind, int level, String marker, List<Span> spans) {
        public Block { spans = List.copyOf(spans); }
    }

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+)$");
    private static final Pattern LIST = Pattern.compile("^([-+*]|[0-9]+\\.)[ \\t]+(.+)$");
    private static final Pattern INLINE = Pattern.compile("`([^`\\n]+)`|\\*\\*([^*\\n]+)\\*\\*|__([^_\\n]+)__|\\*([^*\\n]+)\\*|_([^_\\n]+)_");

    public static List<Block> parse(String source) {
        Objects.requireNonNull(source);
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean fenced = false;
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (line.startsWith("```")) {
                flush(blocks, paragraph);
                if (fenced) {
                    blocks.add(new Block(Kind.CODE, 0, "", List.of(new Span(code.toString(), Style.CODE))));
                    code.setLength(0);
                }
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                code.append(line).append('\n');
                continue;
            }
            if (line.isBlank()) {
                flush(blocks, paragraph);
                continue;
            }
            var heading = HEADING.matcher(line);
            var list = LIST.matcher(line);
            if (heading.matches()) {
                flush(blocks, paragraph);
                blocks.add(new Block(Kind.HEADING, heading.group(1).length(), "", inline(heading.group(2))));
            } else if (list.matches()) {
                flush(blocks, paragraph);
                String marker = list.group(1);
                blocks.add(new Block(Kind.LIST_ITEM, 0, marker.length() == 1 ? "•" : marker, inline(list.group(2))));
            } else if (line.equals(">") || line.startsWith("> ")) {
                flush(blocks, paragraph);
                blocks.add(new Block(Kind.QUOTE, 0, "", inline(line.length() > 1 ? line.substring(2) : "")));
            } else {
                if (!paragraph.isEmpty()) paragraph.append('\n');
                paragraph.append(line);
            }
        }
        if (fenced) blocks.add(new Block(Kind.CODE, 0, "", List.of(new Span(code.toString(), Style.CODE))));
        flush(blocks, paragraph);
        return List.copyOf(blocks);
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new Block(Kind.PARAGRAPH, 0, "", inline(paragraph.toString())));
            paragraph.setLength(0);
        }
    }

    static List<Span> inline(String source) {
        var matcher = INLINE.matcher(source);
        List<Span> spans = new ArrayList<>();
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() > end) spans.add(new Span(source.substring(end, matcher.start()), Style.NORMAL));
            if (matcher.group(1) != null) spans.add(new Span(matcher.group(1), Style.CODE));
            else if (matcher.group(2) != null) spans.add(new Span(matcher.group(2), Style.BOLD));
            else if (matcher.group(3) != null) spans.add(new Span(matcher.group(3), Style.BOLD));
            else if (matcher.group(4) != null) spans.add(new Span(matcher.group(4), Style.ITALIC));
            else spans.add(new Span(matcher.group(5), Style.ITALIC));
            end = matcher.end();
        }
        if (end < source.length()) spans.add(new Span(source.substring(end), Style.NORMAL));
        return List.copyOf(spans);
    }
}
