package io.autocrypt.jwlee.cowork.core.tools;

import java.util.regex.Pattern;

/**
 * Port of Claude Code's FileEditTool utilities for surgical edits.
 * Handles quote normalization and typography preservation.
 */
public class StringEditUtils {

    public static final String LEFT_SINGLE_CURLY_QUOTE = "‘";
    public static final String RIGHT_SINGLE_CURLY_QUOTE = "’";
    public static final String LEFT_DOUBLE_CURLY_QUOTE = "“";
    public static final String RIGHT_DOUBLE_CURLY_QUOTE = "”";

    /**
     * Normalizes curly quotes to straight quotes.
     */
    public static String normalizeQuotes(String str) {
        if (str == null) return null;
        return str.replace(LEFT_SINGLE_CURLY_QUOTE, "'")
                  .replace(RIGHT_SINGLE_CURLY_QUOTE, "'")
                  .replace(LEFT_DOUBLE_CURLY_QUOTE, "\"")
                  .replace(RIGHT_DOUBLE_CURLY_QUOTE, "\"");
    }

    /**
     * Finds the actual string in the file content, accounting for quote differences.
     * Based on Claude Code's exact algorithm.
     */
    public static String findActualString(String fileContent, String searchString) {
        if (fileContent == null || searchString == null) return null;
        
        // 1. Exact match
        if (fileContent.contains(searchString)) {
            return searchString;
        }

        // 2. Normalized match (Curly vs Straight quotes)
        String normalizedSearch = normalizeQuotes(searchString);
        String normalizedFile = normalizeQuotes(fileContent);

        int index = normalizedFile.indexOf(normalizedSearch);
        if (index != -1) {
            // Find the actual original string in the file
            return fileContent.substring(index, index + searchString.length());
        }

        return null;
    }

    /**
     * Preserves the curly quote style of the original string in the replacement string.
     */
    public static String preserveQuoteStyle(String oldString, String actualOldString, String newString) {
        if (oldString.equals(actualOldString)) {
            return newString;
        }

        boolean hasDouble = actualOldString.contains(LEFT_DOUBLE_CURLY_QUOTE) || 
                          actualOldString.contains(RIGHT_DOUBLE_CURLY_QUOTE);
        boolean hasSingle = actualOldString.contains(LEFT_SINGLE_CURLY_QUOTE) || 
                          actualOldString.contains(RIGHT_SINGLE_CURLY_QUOTE);

        if (!hasDouble && !hasSingle) {
            return newString;
        }

        String result = newString;
        if (hasDouble) result = applyCurlyQuotes(result, "\"", LEFT_DOUBLE_CURLY_QUOTE, RIGHT_DOUBLE_CURLY_QUOTE, false);
        if (hasSingle) result = applyCurlyQuotes(result, "'", LEFT_SINGLE_CURLY_QUOTE, RIGHT_SINGLE_CURLY_QUOTE, true);

        return result;
    }

    private static String applyCurlyQuotes(String str, String target, String open, String close, boolean handleApostrophes) {
        StringBuilder sb = new StringBuilder();
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (String.valueOf(chars[i]).equals(target)) {
                if (handleApostrophes && isApostrophe(chars, i)) {
                    sb.append(RIGHT_SINGLE_CURLY_QUOTE);
                } else {
                    sb.append(isOpeningContext(chars, i) ? open : close);
                }
            } else {
                sb.append(chars[i]);
            }
        }
        return sb.toString();
    }

    private static boolean isOpeningContext(char[] chars, int index) {
        if (index == 0) return true;
        char prev = chars[index - 1];
        return Character.isWhitespace(prev) || "([{—–".indexOf(prev) != -1;
    }

    private static boolean isApostrophe(char[] chars, int index) {
        if (index == 0 || index == chars.length - 1) return false;
        return Character.isLetter(chars[index - 1]) && Character.isLetter(chars[index + 1]);
    }
}
