package io.autocrypt.jwlee.cowork.agents.translate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TranslatePdfParser {

    /**
     * Extracts the text of the first N pages to be sent to the LLM for context extraction.
     * Uses PDFBox for quick extraction of initial pages.
     */
    public String extractInitialPagesForLlm(File pdfFile, int maxPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(maxPages, document.getNumberOfPages()));
            return stripper.getText(document);
        }
    }

    /**
     * Parses the PDF using a Python script (PyMuPDF) and returns the full Markdown content.
     */
    public String parsePdfToMarkdown(File pdfFile, Path imageOutputDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            ".venv/bin/python",
            "scripts/translate_pdf_parser.py",
            pdfFile.getAbsolutePath(),
            imageOutputDir.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Python parser failed with exit code " + exitCode + ". Output: " + output);
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python parser was interrupted", e);
        }
    }

    /**
     * Compatibility method for TranslateAgent.
     */
    public List<PdfElement> parsePdf(File pdfFile, Path imageOutputDir, List<String> boilerplatePatterns) throws IOException {
        String markdown = parsePdfToMarkdown(pdfFile, imageOutputDir);
        List<PdfElement> elements = new ArrayList<>();
        // Wrap the whole markdown content as a single element of type "markdown".
        // This is handled by TranslateAgent.chunkElements.
        elements.add(new PdfElement("markdown", markdown));
        return elements;
    }

    public record PdfElement(String type, String content) {}
}
