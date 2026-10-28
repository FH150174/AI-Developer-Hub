package com.fhaicl.aidevhub.notes;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public final class MarkdownPreview extends ScrollPane {
    private final VBox document = new VBox(10);

    public MarkdownPreview() {
        setFitToWidth(true);
        setMinHeight(100);
        document.setPadding(new Insets(12));
        setContent(document);
        showMarkdown("");
    }

    public void showMarkdown(String source) {
        document.getChildren().clear();
        var blocks = MarkdownParser.parse(source);
        if (blocks.isEmpty()) document.getChildren().add(new Label("暂无预览内容"));
        for (var block : blocks) {
            TextFlow flow = new TextFlow();
            if (!block.marker().isEmpty()) flow.getChildren().add(new Text(block.marker() + " "));
            for (var span : block.spans()) {
                Text text = new Text(span.text());
                boolean code = span.style() == MarkdownParser.Style.CODE;
                double size = block.kind() == MarkdownParser.Kind.HEADING ? 28 - 2 * block.level() : 14;
                text.setFont(Font.font(code ? "Monospaced" : Font.getDefault().getFamily(),
                        span.style() == MarkdownParser.Style.BOLD || block.kind() == MarkdownParser.Kind.HEADING
                                ? FontWeight.BOLD : FontWeight.NORMAL,
                        span.style() == MarkdownParser.Style.ITALIC ? FontPosture.ITALIC : FontPosture.REGULAR, size));
                flow.getChildren().add(text);
            }
            if (block.kind() == MarkdownParser.Kind.CODE) {
                flow.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 10;");
            } else if (block.kind() == MarkdownParser.Kind.QUOTE) {
                flow.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 0 0 0 3; -fx-padding: 0 0 0 10;");
            }
            document.getChildren().add(flow);
        }
        setVvalue(0);
    }
}
