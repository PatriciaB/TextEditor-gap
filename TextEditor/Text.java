import java.awt.Font;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Text {
    private List<StyledChar> characters;

    // Application-wide clipboard
    public static List<StyledChar> clipboard = new ArrayList<>();


    public void saveWithFontInfo(String filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            // Write font/style/size ranges
            Font currentFont = null;
            int rangeStart = 0;

            for (int i = 0; i <= characters.size(); i++) {
                Font font = (i < characters.size()) ? characters.get(i).font : null;

                // Check if the font has changed (name, style, or size) or if it's the end of the text
                // Font.equals() compares name, style, and size
                if (i == characters.size() || (currentFont != null && !font.equals(currentFont))) {
                    if (currentFont != null) { // Ensure we have a valid range to write
                        // Write the style range with size
                        writer.write(String.format("%d,%d,%s,%d,%d\n",
                                rangeStart,
                                i,
                                currentFont.getName(),
                                currentFont.getStyle(),
                                currentFont.getSize())); // Add size here
                    }
                    rangeStart = i;
                }
                currentFont = (i < characters.size()) ? font : null;
            }

            // Write text separator
            writer.write("---\n");

            // Write actual text content
            writer.write(getText());
        }
    }


    public Text(String filePath) {
        characters = new ArrayList<>();
        Font defaultFont = new Font("Monospaced", Font.PLAIN, 14); // Default font size for plain text or missing info

        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));

            // Check if this *might* be a styled file format by looking for the separator
            int separatorIndex = lines.indexOf("---");
            boolean isStyledFileFormat = separatorIndex != -1; // Separator indicates the format

            if (isStyledFileFormat) {
                // This *might* be a styled file, attempt to parse font info before the separator
                Map<Integer, Font> fontMap = new HashMap<>();

                // Parse lines *before* the separator for font information
                for (int i = 0; i < separatorIndex; i++) {
                    String line = lines.get(i);
                    String[] parts = line.split(",");

                    int start = -1, end = -1, style = -1, size = defaultFont.getSize(); // Default size
                    String fontName = null;
                    boolean parsedSuccessfully = false;

                    try {
                        if (parts.length >= 4) {
                            start = Integer.parseInt(parts[0].trim());
                            end = Integer.parseInt(parts[1].trim());
                            fontName = parts[2].trim();
                            style = Integer.parseInt(parts[3].trim());

                            if (parts.length >= 5) {
                                // Size information is present
                                size = Integer.parseInt(parts[4].trim());
                            }

                            // Validate basic range sanity
                            if (start >= 0 && end > start) {
                                Font font = new Font(fontName, style, size);
                                for (int pos = start; pos < end; pos++) {
                                    fontMap.put(pos, font);
                                }
                                parsedSuccessfully = true;
                            } else {
                                System.err.println("Warning: Invalid range [" + start + "," + end + "] in font info line: " + line);
                            }

                        } else {
                            System.err.println("Warning: Malformed font info line (less than 4 parts): " + line);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing numbers in font info line: " + line + " - " + e.getMessage());
                        // continue parsing other lines
                    } catch (IllegalArgumentException e) {
                        System.err.println("Error creating Font from info line: " + line + " - " + e.getMessage());
                        // This might happen if fontName or style is invalid
                        // continue parsing other lines
                    }
                }

                // Read content from lines *after* the separator
                StringBuilder content = new StringBuilder();
                for (int i = separatorIndex + 1; i < lines.size(); i++) {
                    content.append(lines.get(i));
                    if (i < lines.size() - 1) { // Add newline for all lines except the very last one
                        content.append("\n");
                    }
                }
                String text = content.toString();


                // Create styled characters, applying parsed fonts or default
                for (int i = 0; i < text.length(); i++) {
                    // Get the specific font for this position, or use default if not found
                    Font font = fontMap.getOrDefault(i, defaultFont);
                    characters.add(new StyledChar(text.charAt(i), font));
                }

            } else {
                StringBuilder plainContent = new StringBuilder();
                for(int i = 0; i < lines.size(); i++){
                    plainContent.append(lines.get(i));
                    if(i < lines.size() - 1){
                        plainContent.append("\n");
                    }
                }
                for(char c : plainContent.toString().toCharArray()){
                    characters.add(new StyledChar(c, defaultFont));
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            // Initialize with empty content on file reading error
            characters = new ArrayList<>();
        }
    }




    public int getLength() {
        return characters.size();
    }

    public char charAt(int index) {
        return characters.get(index).character;
    }

    public StyledChar getStyledChar(int index) {
        return characters.get(index);
    }

    public void insert(int pos, String text, Font font) {
        for (int i = 0; i < text.length(); i++) {
            characters.add(pos + i, new StyledChar(text.charAt(i), font));
        }
    }

    public void delete(int pos, int length) {
        for (int i = 0; i < length && pos < characters.size(); i++) {
            characters.remove(pos);
        }
    }

    public void setFontRange(int start, int end, Font font) {
        for (int i = start; i < end && i < characters.size(); i++) {
            characters.get(i).font = font;
        }
    }

    public int getLineCount() {
        int lines = 1;
        for (StyledChar sc : characters) {
            if (sc.character == '\n') lines++;
        }
        return lines;
    }

    public List<StyledChar> getLine(int index) {
        int line = 0;
        int i = 0;
        List<StyledChar> result = new ArrayList<>();

        while (i < characters.size() && line < index) {
            if (characters.get(i).character == '\n') line++;
            i++;
        }

        while (i < characters.size() && characters.get(i).character != '\n') {
            result.add(characters.get(i));
            i++;
        }

        return result;
    }

    // === Clipboard Operations ===

    public void copy(int start, int end) {
        clipboard.clear();
        for (int i = start; i < end && i < characters.size(); i++) {
            StyledChar original = characters.get(i);
            clipboard.add(new StyledChar(original.character, original.font));
        }
    }

    public void cut(int start, int end) {
        copy(start, end);
        delete(start, end - start);
    }

    public void paste(int pos) {
        for (int i = 0; i < clipboard.size(); i++) {
            StyledChar sc = clipboard.get(i);
            characters.add(pos + i, new StyledChar(sc.character, sc.font));
        }
    }

    // === Method to return the text as a String ===
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (StyledChar sc : characters) {
            sb.append(sc.character);
        }
        return sb.toString();
    }


    public int find(int startPos, String searchString) {
        if (searchString == null || searchString.isEmpty()) {
            return -1;
        }

        String textContent = getText();
        int foundPos = textContent.indexOf(searchString, startPos);
        return foundPos;
    }


    // === Nested class for styled characters ===
    public static class StyledChar {
        public char character;
        public Font font;

        public StyledChar(char character, Font font) {
            this.character = character;
            this.font = font;
        }
    }
}
