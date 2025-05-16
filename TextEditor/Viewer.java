import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Viewer extends JPanel {
    private Text text;
    private JScrollBar scrollBar;
    private int caretPosition = 0;

    public int selectionStart = -1;
    public int selectionEnd = -1;

    private final int lineHeight = 20;
    private final int margin = 10;

    public Viewer(Text text, JScrollBar scrollBar) {
        this.text = text;
        this.scrollBar = scrollBar;

        scrollBar.addAdjustmentListener(e -> repaint());
    }

    public void setCaretPosition(int pos) {
        caretPosition = pos;
    }

    public void setText(Text newText) {
        this.text = newText;
    }

    public void setSelection(int start, int end) {
        this.selectionStart = start;
        this.selectionEnd = end;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(600, text.getLineCount() * lineHeight + 20);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int topLine = scrollBar.getValue() / lineHeight;
        int lineCount = getHeight() / lineHeight;

        int pos = 0;
        int y = margin;

        for (int lineIdx = topLine; lineIdx < topLine + lineCount && lineIdx < text.getLineCount(); lineIdx++) {
            List<Text.StyledChar> line = text.getLine(lineIdx);
            int x = margin;

            for (int i = 0; i < line.size(); i++) {
                Text.StyledChar sc = line.get(i);
                g.setFont(sc.font);

                int globalPos = pos + i;

                if (selectionStart >= 0 && globalPos >= selectionStart && globalPos < selectionEnd) {
                    FontMetrics fm = g.getFontMetrics(sc.font);
                    int charWidth = fm.charWidth(sc.character);
                    g.setColor(new Color(180, 200, 255)); // highlight
                    g.fillRect(x, y - lineHeight + 5, charWidth, lineHeight);
                    g.setColor(Color.BLACK);
                }

                g.drawString(String.valueOf(sc.character), x, y);
                x += g.getFontMetrics(sc.font).charWidth(sc.character);
            }

            pos += line.size() + 1;
            y += lineHeight;
        }

        // Draw caret
        if (caretPosition >= 0 && caretPosition <= text.getLength()) {
            int caretLine = 0, caretOffset = caretPosition;
            int caretX = margin;
            int caretY = margin;

            int index = 0;
            while (index < caretPosition && caretLine < text.getLineCount()) {
                List<Text.StyledChar> line = text.getLine(caretLine);
                int lineLen = line.size() + 1;
                if (caretOffset < lineLen) break;
                caretOffset -= lineLen;
                caretLine++;
                index += lineLen;
            }

            if (caretLine < text.getLineCount()) {
                List<Text.StyledChar> line = text.getLine(caretLine);
                for (int i = 0; i < caretOffset && i < line.size(); i++) {
                    Text.StyledChar sc = line.get(i);
                    caretX += g.getFontMetrics(sc.font).charWidth(sc.character);
                }

                caretY = (caretLine - topLine + 1) * lineHeight;
                g.setColor(Color.BLACK);
                g.drawLine(caretX, caretY - lineHeight + 5, caretX, caretY - 5);
            }
        }
    }
}
