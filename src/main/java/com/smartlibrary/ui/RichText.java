package com.smartlibrary.ui;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a tiny piece of markup - plain text with optional &lt;b&gt;bold&lt;/b&gt;
 * runs - into a TextFlow that wraps inside its pane.
 *
 * A JavaFX Label only understands HTML when the whole text is wrapped in
 * &lt;html&gt; tags (and then it ignores the available width while wrapping),
 * so the User Guide builds its paragraphs with Text nodes instead: the bold
 * screen names keep their emphasis and the text still breaks correctly in the
 * detail card.
 */
public final class RichText {

    /** Splits "plain <b>bold</b> tail" into its parts. */
    private static final Pattern PARTS = Pattern.compile("<b>.*?</b>|[^<]+");

    private static final Font NORMAL = Font.font(null, FontWeight.NORMAL, 13.5);
    private static final Font BOLD = Font.font(null, FontWeight.BOLD, 13.5);
    private static final Color INK = Color.web("#41505f");

    private RichText() {
    }

    /** Builds a wrapping, line-spaced flow for the given markup. */
    public static TextFlow flow(String markup) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(Double.MAX_VALUE);
        flow.setLineSpacing(4);

        Matcher matcher = PARTS.matcher(markup == null ? "" : markup);
        while (matcher.find()) {
            String part = matcher.group();
            boolean bold = part.startsWith("<b>");
            if (bold) {
                part = part.substring(3, part.length() - 4); // strip <b> ... </b>
            }
            Text text = new Text(part);
            text.setFont(bold ? BOLD : NORMAL);
            text.setFill(INK);
            flow.getChildren().add(text);
        }
        return flow;
    }
}
