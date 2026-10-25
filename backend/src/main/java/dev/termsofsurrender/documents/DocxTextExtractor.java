package dev.termsofsurrender.documents;

import dev.termsofsurrender.api.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Bounded, text-only WordprocessingML import. Never resolves relationships or writes ZIP entries.
 */
final class DocxTextExtractor {
    private static final String WORD =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_EXPANDED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_XML_BYTES = 4 * 1024 * 1024;
    private static final Set<String> OMITTED_CONTENT =
            Set.of("drawing", "pict", "object", "txbxContent");
    private static final Set<String> REVISIONS =
            Set.of(
                    "ins",
                    "del",
                    "delText",
                    "delInstrText",
                    "moveFrom",
                    "moveTo",
                    "moveFromRangeStart",
                    "moveToRangeStart",
                    "numberingChange",
                    "tblGridChange",
                    "tblPrExChange",
                    "pPrChange",
                    "rPrChange",
                    "sectPrChange",
                    "tblPrChange",
                    "trPrChange",
                    "tcPrChange",
                    "cellIns",
                    "cellDel",
                    "cellMerge");

    String extract(byte[] bytes) {
        byte[] document = null;
        Set<String> names = new HashSet<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (names.size() >= MAX_ENTRIES || !names.add(entry.getName())) {
                    throw invalid("The DOCX contains too many or duplicate archive entries.");
                }
                if (entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith("vbaproject.bin")) {
                    throw invalid(
                            "Macro-enabled documents are not supported. Export a plain DOCX or PDF.");
                }
                boolean main = entry.getName().equals("word/document.xml");
                ByteArrayOutputStream output = main ? new ByteArrayOutputStream() : null;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_EXPANDED_BYTES) {
                        throw invalid("The expanded DOCX exceeds the 16 MB extraction budget.");
                    }
                    if (main) {
                        if (output.size() + count > MAX_XML_BYTES) {
                            throw invalid("The DOCX body exceeds the 4 MB XML budget.");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                if (main) {
                    document = output.toByteArray();
                }
            }
        } catch (IOException exception) {
            throw invalid("The DOCX archive could not be read. It may be damaged or encrypted.");
        }
        if (document == null
                || !names.contains("[Content_Types].xml")
                || !names.contains("_rels/.rels")) {
            throw invalid("Upload a DOCX containing a standard Word document body.");
        }
        return readBody(document);
    }

    private String readBody(byte[] document) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver(
                (publicId, systemId, base, namespace) -> {
                    throw new XMLStreamException("External entities are forbidden");
                });
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(document));
            StringBuilder text = new StringBuilder();
            int depth = 0;
            int events = 0;
            int bodyDepth = -1;
            int textDepth = -1;
            int runDepth = -1;
            int omittedDepth = -1;
            boolean root = false;
            boolean body = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (++events > 500_000) {
                    throw invalid("The DOCX XML is too complex.");
                }
                if (event == XMLStreamConstants.DTD
                        || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw invalid(
                            "DOCX XML entities and document type declarations are not supported.");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > 128) {
                        throw invalid("The DOCX XML is nested too deeply.");
                    }
                    boolean word = WORD.equals(reader.getNamespaceURI());
                    String name = reader.getLocalName();
                    if (depth == 1) {
                        root = word && name.equals("document");
                        if (!root) {
                            throw invalid("The DOCX body is not supported WordprocessingML.");
                        }
                    }
                    if (word && REVISIONS.contains(name)) {
                        throw ApiException.invalid(
                                "DOCX_TRACKED_CHANGES",
                                "This DOCX contains tracked changes. Accept or reject changes in Word, then upload again.");
                    }
                    if (word && name.equals("body") && depth == 2) {
                        if (body) {
                            throw invalid("The DOCX contains multiple document bodies.");
                        }
                        body = true;
                        bodyDepth = depth;
                    }
                    if (bodyDepth > 0 && omittedDepth < 0) {
                        // Do not concatenate both Choice and Fallback representations of a shape.
                        boolean alternateContent =
                                "http://schemas.openxmlformats.org/markup-compatibility/2006"
                                                .equals(reader.getNamespaceURI())
                                        && name.equals("AlternateContent");
                        if ((word && OMITTED_CONTENT.contains(name)) || alternateContent) {
                            omittedDepth = depth;
                        } else if (word && name.equals("r")) {
                            runDepth = depth;
                        } else if (word && name.equals("t") && depth == runDepth + 1) {
                            textDepth = depth;
                        } else if (word
                                && runDepth > 0
                                && depth == runDepth + 1
                                && (name.equals("tab") || name.equals("br") || name.equals("cr"))) {
                            text.append(name.equals("tab") ? '\t' : '\n');
                        }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    if (textDepth > 0 && omittedDepth < 0) {
                        text.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (bodyDepth > 0
                            && omittedDepth < 0
                            && WORD.equals(reader.getNamespaceURI())) {
                        if (reader.getLocalName().equals("p")) {
                            text.append("\n\n");
                        }
                        if (reader.getLocalName().equals("tc")) {
                            text.append('\t');
                        }
                    }
                    if (depth == textDepth) {
                        textDepth = -1;
                    }
                    if (depth == runDepth) {
                        runDepth = -1;
                    }
                    if (depth == omittedDepth) {
                        omittedDepth = -1;
                    }
                    if (depth == bodyDepth) {
                        bodyDepth = -1;
                    }
                    depth--;
                }
                // Leave room for structural separators that canonicalization trims. The shared
                // builder enforces the exact 80,000-character canonical limit afterwards.
                if (text.length() > DocumentExtractor.MAX_CHARACTERS * 2) {
                    throw ApiException.invalid(
                            "DOCUMENT_TOO_LONG",
                            "This release supports up to 80,000 characters per document.");
                }
            }
            if (!root || !body) {
                throw invalid("The DOCX has no readable document body.");
            }
            return text.toString();
        } catch (XMLStreamException exception) {
            throw invalid("The DOCX XML could not be read safely. Export a plain DOCX or PDF.");
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    /* In-memory input only. */
                }
            }
        }
    }

    private ApiException invalid(String message) {
        return ApiException.invalid("UNREADABLE_DOCX", message);
    }
}
