package com.school.student_manage.ai.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文友好文本切片器：按段落聚合到目标长度，段落过长时按句号切分，保留重叠以维持语义连续。
 */
public final class TextChunker {

    private TextChunker() {
    }

    public static List<String> chunk(String content, int maxLength, int overlap) {
        List<String> chunks = new ArrayList<String>();
        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }
        String normalized = content.replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > maxLength) {
                for (String piece : splitLongParagraph(text, maxLength)) {
                    if (current.length() > 0) {
                        chunks.add(current.toString().trim());
                        current.setLength(0);
                    }
                    chunks.add(piece);
                }
                continue;
            }
            if (current.length() + text.length() + 1 > maxLength && current.length() > 0) {
                chunks.add(current.toString().trim());
                String tail = tail(current.toString(), overlap);
                current.setLength(0);
                if (!tail.isEmpty()) {
                    current.append(tail).append('\n');
                }
            }
            current.append(text).append('\n');
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        List<String> cleaned = new ArrayList<String>();
        for (String chunk : chunks) {
            if (chunk.length() >= 10) {
                cleaned.add(chunk);
            }
        }
        return cleaned;
    }

    private static List<String> splitLongParagraph(String text, int maxLength) {
        List<String> pieces = new ArrayList<String>();
        StringBuilder buffer = new StringBuilder();
        String[] sentences = text.split("(?<=[。！？；!?;])");
        for (String sentence : sentences) {
            if (buffer.length() + sentence.length() > maxLength && buffer.length() > 0) {
                pieces.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            if (sentence.length() > maxLength) {
                int start = 0;
                while (start < sentence.length()) {
                    int end = Math.min(start + maxLength, sentence.length());
                    pieces.add(sentence.substring(start, end));
                    start = end;
                }
                continue;
            }
            buffer.append(sentence);
        }
        if (buffer.length() > 0) {
            pieces.add(buffer.toString().trim());
        }
        return pieces;
    }

    private static String tail(String text, int overlap) {
        if (text.length() <= overlap) {
            return text;
        }
        return text.substring(text.length() - overlap);
    }
}
