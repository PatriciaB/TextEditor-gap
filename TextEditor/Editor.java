import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Editor {
    private static int caretPosition = 0;
    private static int selectionAnchor = -1;
    private static boolean shiftPressed = false;

    private static String lastSearchText = "";
    private static int lastSearchPosition = 0;

    private static Text text;        // Declare text as an instance variable
    private static Viewer viewer;    // Declare viewer as an instance variable
    private static JScrollBar scrollBar;  // Declare scrollBar as an instance variable

    public static void main(String[] arg) {
        if (arg.length < 1) {
            System.out.println("-- file name missing");
            return;
        }

        String path = arg[0];
        text = new Text(path);  // Use the static variable
        scrollBar = new JScrollBar(Adjustable.VERTICAL, 0, 0, 0, 1000);
        viewer = new Viewer(text, scrollBar);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add("Center", viewer);
        panel.add("East", scrollBar);

        JFrame frame = new JFrame(path);
        frame.setSize(700, 800);
        frame.setContentPane(panel);

        // === Menu Bar ===
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openFile(frame));

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> saveFile(frame));

        fileMenu.add(openItem);
        fileMenu.add(saveItem);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.addActionListener(e -> {
            if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
                text.cut(viewer.selectionStart, viewer.selectionEnd);
                caretPosition = viewer.selectionStart;
                viewer.setSelection(-1, -1);
                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
            }
        });

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> {
            if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
                text.copy(viewer.selectionStart, viewer.selectionEnd);
            }
        });

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.addActionListener(e -> {
            int pastePosition = (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart)
                    ? viewer.selectionStart
                    : caretPosition;
            text.paste(pastePosition);
            caretPosition = pastePosition + Text.clipboard.size();
            viewer.setSelection(-1, -1);
            viewer.setCaretPosition(caretPosition);
            viewer.repaint();
        });

        JMenuItem findItem = new JMenuItem("Find");
        findItem.addActionListener(e -> showFindDialog(frame));


        JMenuItem findNextItem = new JMenuItem("Find Next");
        findNextItem.addActionListener(e -> findNextOccurrence(frame));

        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(findItem);
        editMenu.add(findNextItem);

        JMenu fontFamilyMenu = new JMenu("Font Family");
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String fontName : ge.getAvailableFontFamilyNames()) {
            JMenuItem fontItem = new JMenuItem(fontName);
            fontItem.addActionListener(e -> setSelectionFont(fontName, -1, -1));
            fontFamilyMenu.add(fontItem);
        }

        // Format Menu
        JMenu formatMenu = new JMenu("Format");

        // Font Size Submenu
        JMenu fontSizeMenu = new JMenu("Font Size");
        int[] sizes = {8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 48, 72};
        for (int size : sizes) {
            JMenuItem sizeItem = new JMenuItem(String.valueOf(size));
            sizeItem.addActionListener(e -> setSelectionFont(null, size, -1));
            fontSizeMenu.add(sizeItem);
        }

        // Font Style Items
        JCheckBoxMenuItem boldItem = new JCheckBoxMenuItem("Bold");
        boldItem.addActionListener(e -> toggleFontStyle(Font.BOLD, boldItem.isSelected()));
        JCheckBoxMenuItem italicItem = new JCheckBoxMenuItem("Italic");
        italicItem.addActionListener(e -> toggleFontStyle(Font.ITALIC, italicItem.isSelected()));

        formatMenu.add(fontFamilyMenu);
        formatMenu.add(fontSizeMenu);
        formatMenu.addSeparator();
        formatMenu.add(boldItem);
        formatMenu.add(italicItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(formatMenu);
        frame.setJMenuBar(menuBar);


// Add keyboard shortcut (F3)
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "findNext");
        frame.getRootPane().getActionMap().put("findNext", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                findNextOccurrence(frame);
            }
        });

        frame.setJMenuBar(menuBar);

        // === Key Handling ===
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char keyChar = e.getKeyChar();
                if (keyChar == KeyEvent.VK_BACK_SPACE) {
                    if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
                        text.delete(viewer.selectionStart, viewer.selectionEnd - viewer.selectionStart);
                        caretPosition = viewer.selectionStart;
                    } else if (caretPosition > 0) {
                        text.delete(caretPosition - 1, 1);
                        caretPosition--;
                    }
                } else if (keyChar == KeyEvent.VK_ENTER) {
                    Font currentFont = new Font("Monospaced", Font.PLAIN, 14);
                    text.insert(caretPosition, "\n", currentFont);
                    caretPosition++;
                } else if (!Character.isISOControl(keyChar)) {
                    Font currentFont = new Font("Monospaced", Font.PLAIN, 14);
                    text.insert(caretPosition, String.valueOf(keyChar), currentFont);
                    caretPosition++;
                }

                // Clear selection after any typing
                selectionAnchor = -1;
                viewer.setSelection(-1, -1);
                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                int oldCaretPosition = caretPosition;

                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftPressed = true;
                    if (selectionAnchor == -1) {
                        selectionAnchor = caretPosition;  // Set anchor on first Shift press
                    }
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_LEFT && caretPosition > 0) {
                    caretPosition--;
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT && caretPosition < text.getLength()) {
                    caretPosition++;
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    // Move caret up one line (existing code)
                    int line = 0;
                    int pos = 0;
                    while (pos + text.getLine(line).size() + 1 <= caretPosition) {
                        pos += text.getLine(line).size() + 1;
                        line++;
                    }
                    if (line > 0) {
                        int offset = caretPosition - pos;
                        java.util.List<Text.StyledChar> prevLine = text.getLine(line - 1);
                        caretPosition = pos - text.getLine(line - 1).size() - 1 + Math.min(offset, prevLine.size());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // Move caret down one line (existing code)
                    int line = 0;
                    int pos = 0;
                    while (pos + text.getLine(line).size() + 1 <= caretPosition) {
                        pos += text.getLine(line).size() + 1;
                        line++;
                    }
                    if (line < text.getLineCount() - 1) {
                        int offset = caretPosition - pos;
                        java.util.List<Text.StyledChar> nextLine = text.getLine(line + 1);
                        caretPosition = pos + text.getLine(line).size() + 1 + Math.min(offset, nextLine.size());
                    }
                }

                // Handle selection
                if (shiftPressed) {
                    if (selectionAnchor == -1) {
                        selectionAnchor = oldCaretPosition;
                    }
                    viewer.setSelection(Math.min(selectionAnchor, caretPosition),
                            Math.max(selectionAnchor, caretPosition));
                } else {
                    selectionAnchor = -1;
                    viewer.setSelection(-1, -1);
                }

                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftPressed = false;
                }
            }
        });


        // === Mouse Handling ===
        viewer.addMouseListener(new MouseAdapter() {
            private long lastClickTime = 0;

            @Override
            public void mousePressed(MouseEvent e) {
                int y = e.getY();
                int x = e.getX();
                int lineHeight = 20;

                int clickedLine = (scrollBar.getValue() / lineHeight) + (y / lineHeight);
                java.util.List<Text.StyledChar> line = text.getLine(clickedLine);
                int width = 10;
                int charOffset = 0;

                while (charOffset < line.size()) {
                    FontMetrics fm = viewer.getFontMetrics(line.get(charOffset).font);
                    int charWidth = fm.charWidth(line.get(charOffset).character);
                    if (width + charWidth / 2 >= x) break;
                    width += charWidth;
                    charOffset++;
                }

                int newCaret = 0;
                for (int i = 0; i < clickedLine; i++) {
                    newCaret += text.getLine(i).size() + 1;
                }
                newCaret += charOffset;
                caretPosition = Math.min(newCaret, text.getLength());

                long now = System.currentTimeMillis();
                if (now - lastClickTime < 400) { // double-click
                    int start = caretPosition;
                    int end = caretPosition;
                    while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) start--;
                    while (end < text.getLength() && Character.isLetterOrDigit(text.charAt(end))) end++;
                    viewer.setSelection(start, end);
                } else {
                    viewer.setSelection(-1, -1);
                }

                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
                lastClickTime = now;
            }
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void openFile(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open Text File");
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            text = new Text(path);
            viewer.setText(text); // Add a setter method in Viewer to update the text
            viewer.repaint();
            caretPosition = 0;
            viewer.setCaretPosition(caretPosition);
            viewer.setSelection(-1, -1);
        }
    }

    private static void saveFile(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Text File");
        int result = fileChooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                text.saveWithFontInfo(selectedFile.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                        "Error saving file with font information: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showFindDialog(JFrame parent) {
        String searchText = JOptionPane.showInputDialog(parent, "Find:", "Find", JOptionPane.PLAIN_MESSAGE);

        if (searchText == null || searchText.isEmpty()) return;

        lastSearchText = searchText;
        lastSearchPosition = caretPosition;

        findNextOccurrence(parent);
    }

    private static void findNextOccurrence(JFrame parent) {
        if (lastSearchText.isEmpty()) {
            showFindDialog(parent);
            return;
        }

        String content = text.getText();
        int foundPos = content.indexOf(lastSearchText, lastSearchPosition + 1);

        if (foundPos >= 0) {
            lastSearchPosition = foundPos;
            caretPosition = foundPos;
            viewer.setSelection(foundPos, foundPos + lastSearchText.length());
        } else {
            // Wrap around to start of document
            foundPos = content.indexOf(lastSearchText);
            if (foundPos >= 0) {
                lastSearchPosition = foundPos;
                caretPosition = foundPos;
                viewer.setSelection(foundPos, foundPos + lastSearchText.length());
                JOptionPane.showMessageDialog(parent,
                        "Reached end of document, continued from top",
                        "Find", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Text not found", "Find", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        viewer.setCaretPosition(caretPosition);
        scrollToPosition(caretPosition);
        viewer.repaint();
    }

    private static void scrollToPosition(int position) {
        // Calculate the exact line containing the position
        int line = 0;
        int pos = 0;
        while (line < text.getLineCount()) {
            int lineLength = text.getLine(line).size() + 1; // +1 for newline
            if (pos + lineLength > position) break;
            pos += lineLength;
            line++;
        }

        // Calculate scroll position (in pixels)
        int lineHeight = 20; // Should match your viewer's line height
        int visibleLines = viewer.getHeight() / lineHeight;

        // Center the found line in the view
        int scrollValue = Math.max(0, (line * lineHeight) - (visibleLines/2 * lineHeight));

        // Set scroll position
        scrollBar.setValue(Math.min(scrollValue, scrollBar.getMaximum()));
        viewer.repaint();
    }
    private static void setSelectionFont(String fontName, Integer size, Integer style) {
        if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
            for (int i = viewer.selectionStart; i < viewer.selectionEnd; i++) {
                Text.StyledChar sc = text.getStyledChar(i);
                Font currentFont = sc.font;

                String newName = fontName != null ? fontName : currentFont.getName();
                int newSize = size != -1 ? size : currentFont.getSize();
                int newStyle = style != -1 ? style : currentFont.getStyle();

                sc.font = new Font(newName, newStyle, newSize);
            }
            viewer.repaint();
        }
    }

    private static void toggleFontStyle(int style, boolean set) {
        if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
            for (int i = viewer.selectionStart; i < viewer.selectionEnd; i++) {
                Text.StyledChar sc = text.getStyledChar(i);
                int newStyle = set ? sc.font.getStyle() | style : sc.font.getStyle() & ~style;
                sc.font = sc.font.deriveFont(newStyle);
            }
            viewer.repaint();
        }
    }

}
