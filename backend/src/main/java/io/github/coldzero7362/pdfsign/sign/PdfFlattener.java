package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.audit.AuditEvent;
import io.github.coldzero7362.pdfsign.request.FieldType;
import io.github.coldzero7362.pdfsign.request.Recipient;
import io.github.coldzero7362.pdfsign.request.SignField;
import io.github.coldzero7362.pdfsign.request.SignRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

/**
 * 입력값을 원본 PDF에 그려 넣고(평면화), 마지막에 감사 기록 페이지를 붙인다.
 *
 * 칸 좌표는 "화면에 보이는 방향, 왼쪽 위 원점, pt"다. 페이지에 /Rotate가 있으면
 * 화면 좌표를 PDF 좌표로 바꾸는 변환 행렬을 걸고 그린다.
 */
@Component
public class PdfFlattener {

    private static final DateTimeFormatter KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'")
        .withZone(ZoneId.of("Asia/Seoul"));
    private static final String FONT = "/fonts/NanumGothic-Regular.ttf";

    public record Input(SignRequest request, String documentName, String documentSha256, byte[] original,
                        List<Recipient> recipients, List<SignField> fields,
                        Function<String, byte[]> fileReader, List<AuditEvent> audit) {
    }

    public byte[] flatten(Input in) {
        try (PDDocument doc = Loader.loadPDF(in.original(), "", null, null, IOUtils.createTempFileOnlyStreamCache());
             InputStream fontStream = PdfFlattener.class.getResourceAsStream(FONT)) {
            if (fontStream == null) {
                throw new IllegalStateException("폰트가 없습니다: " + FONT);
            }
            PDFont font = PDType0Font.load(doc, fontStream, true);

            for (SignField f : in.fields()) {
                if (!f.isFilled()) {
                    continue;
                }
                PDPage page = doc.getPage(f.getPageNo() - 1);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                    cs.saveGraphicsState();
                    cs.transform(displayToPdf(page));
                    double displayHeight = displayHeight(page);
                    // 칸의 아래쪽 y (화면 좌표는 위가 0이므로 뒤집는다)
                    float x = (float) f.getX();
                    float yBottom = (float) (displayHeight - f.getY() - f.getHeight());
                    float w = (float) f.getWidth();
                    float h = (float) f.getHeight();
                    switch (f.getType()) {
                        case TEXT, DATE -> drawText(cs, font, f.getTextValue(), x, yBottom, w, h);
                        case CHECKBOX -> drawCheck(cs, x, yBottom, w, h);
                        case SIGNATURE -> drawImage(doc, cs, in.fileReader().apply(f.getFileKey()), x, yBottom, w, h);
                    }
                    cs.restoreGraphicsState();
                }
            }

            appendAuditPage(doc, font, in);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------- 좌표 변환 ----------

    static int rotation(PDPage page) {
        return Math.floorMod(page.getRotation(), 360);
    }

    static double displayHeight(PDPage page) {
        PDRectangle b = page.getCropBox();
        int r = rotation(page);
        return (r == 90 || r == 270) ? b.getWidth() : b.getHeight();
    }

    /**
     * "화면 방향, 왼쪽 아래 원점" 좌표를 PDF 사용자 좌표로 바꾸는 행렬.
     * /Rotate는 시계 방향 회전이다.
     */
    static Matrix displayToPdf(PDPage page) {
        PDRectangle b = page.getCropBox();
        float llx = b.getLowerLeftX();
        float lly = b.getLowerLeftY();
        float w = b.getWidth();
        float h = b.getHeight();
        return switch (rotation(page)) {
            case 90 -> new Matrix(0, 1, -1, 0, llx + w, lly);
            case 180 -> new Matrix(-1, 0, 0, -1, llx + w, lly + h);
            case 270 -> new Matrix(0, -1, 1, 0, llx, lly + h);
            default -> new Matrix(1, 0, 0, 1, llx, lly);
        };
    }

    // ---------- 그리기 ----------

    private static void drawText(PDPageContentStream cs, PDFont font, String value, float x, float y, float w, float h)
            throws IOException {
        if (value == null || value.isBlank()) {
            return;
        }
        String text = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        float pad = Math.min(2f, w / 10);
        float size = Math.min(h * 0.7f, 14f);
        // 칸 폭에 맞을 때까지 글자 크기를 줄이고, 그래도 넘치면 뒤를 자른다
        while (size > 5f && width(font, text, size) > w - 2 * pad) {
            size -= 0.5f;
        }
        while (text.length() > 1 && width(font, text, size) > w - 2 * pad) {
            text = text.substring(0, text.length() - 1);
        }
        float baseline = y + (h - size * 0.8f) / 2;
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x + pad, baseline);
        cs.showText(text);
        cs.endText();
    }

    private static float width(PDFont font, String text, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** 폰트에 체크 기호가 없을 수 있어 선으로 그린다. */
    private static void drawCheck(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        float s = Math.min(w, h);
        float ox = x + (w - s) / 2;
        float oy = y + (h - s) / 2;
        cs.setLineWidth(Math.max(1f, s / 8));
        cs.moveTo(ox + s * 0.15f, oy + s * 0.5f);
        cs.lineTo(ox + s * 0.42f, oy + s * 0.2f);
        cs.lineTo(ox + s * 0.88f, oy + s * 0.85f);
        cs.stroke();
    }

    private static void drawImage(PDDocument doc, PDPageContentStream cs, byte[] png, float x, float y, float w, float h)
            throws IOException {
        if (png == null) {
            return;
        }
        PDImageXObject img = PDImageXObject.createFromByteArray(doc, png, "signature.png");
        // 비율을 지키며 칸 안에 맞추고 가운데 둔다
        float scale = Math.min(w / img.getWidth(), h / img.getHeight());
        float dw = img.getWidth() * scale;
        float dh = img.getHeight() * scale;
        cs.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
    }

    // ---------- 감사 기록 페이지 ----------

    private static void appendAuditPage(PDDocument doc, PDFont font, Input in) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("§감사 기록 (Audit Trail)");
        lines.add("");
        lines.add("요청: " + in.request().getTitle());
        lines.add("요청 ID: " + in.request().getPublicId());
        lines.add("원본 문서: " + in.documentName());
        lines.add("원본 SHA-256: " + in.documentSha256());
        lines.add("");
        lines.add("§참여자");
        for (Recipient r : in.recipients()) {
            String state = switch (r.getStatus()) {
                case COMPLETED -> "완료 " + KST.format(r.getCompletedAt());
                case DECLINED -> "거절";
                default -> r.isSigner() ? "미완료" : "열람자";
            };
            lines.add("- " + r.getName() + " <" + r.getEmail() + "> · " + (r.isSigner() ? "서명자" : "열람자") + " · " + state);
        }
        lines.add("");
        lines.add("§기록");
        Map<Long, Recipient> byId = new java.util.HashMap<>();
        in.recipients().forEach(r -> byId.put(r.getId(), r));
        for (AuditEvent e : in.audit()) {
            StringBuilder sb = new StringBuilder()
                .append(KST.format(e.getCreatedAt())).append("  ")
                .append(eventLabel(e.getEventType())).append("  ")
                .append(e.getActor());
            if (e.getIp() != null) {
                sb.append("  IP ").append(e.getIp());
            }
            lines.add(sb.toString());
            if (e.getDetail() != null) {
                lines.add("    " + e.getDetail());
            }
        }
        lines.add("");
        lines.add("이 페이지는 서비스가 자동으로 만든 기록입니다. 완료본 파일의 SHA-256은 서비스 화면에서 확인할 수 있습니다.");

        float margin = 50;
        float size = 9;
        float leading = 14;
        PDRectangle a4 = PDRectangle.A4;
        float maxWidth = a4.getWidth() - 2 * margin;

        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            wrapped.addAll(wrap(font, line, line.startsWith("§") ? 13 : size, maxWidth));
        }

        PDPageContentStream cs = null;
        float y = 0;
        try {
            for (String line : wrapped) {
                if (cs == null || y < margin) {
                    if (cs != null) {
                        cs.close();
                    }
                    PDPage page = new PDPage(a4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = a4.getHeight() - margin;
                }
                boolean heading = line.startsWith("§");
                String text = heading ? line.substring(1) : line;
                if (!text.isEmpty()) {
                    cs.beginText();
                    cs.setFont(font, heading ? 13 : size);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(text);
                    cs.endText();
                }
                y -= heading ? leading + 4 : leading;
            }
        } finally {
            if (cs != null) {
                cs.close();
            }
        }
    }

    private static List<String> wrap(PDFont font, String line, float size, float maxWidth) throws IOException {
        List<String> out = new ArrayList<>();
        String rest = line;
        boolean heading = rest.startsWith("§");
        String prefix = heading ? "§" : "";
        if (heading) {
            rest = rest.substring(1);
        }
        while (width(font, rest, size) > maxWidth) {
            int cut = rest.length();
            while (cut > 1 && width(font, rest.substring(0, cut), size) > maxWidth) {
                cut--;
            }
            out.add(prefix + rest.substring(0, cut));
            rest = "    " + rest.substring(cut);
            prefix = "";
        }
        out.add(prefix + rest);
        return out;
    }

    private static String eventLabel(String type) {
        return switch (type) {
            case "CREATED" -> "요청 생성";
            case "SENT" -> "발송";
            case "OTP_SENT" -> "인증번호 발송";
            case "OTP_VERIFIED" -> "본인 확인";
            case "OTP_FAILED" -> "인증 실패";
            case "VIEWED" -> "문서 열람";
            case "SUBMITTED" -> "작성 완료";
            case "DECLINED" -> "거절";
            case "COMPLETED" -> "전체 완료";
            case "CANCELED" -> "취소";
            case "MAIL_RESENT" -> "메일 재발송";
            case "EXPIRED" -> "만료";
            default -> type;
        };
    }
}
