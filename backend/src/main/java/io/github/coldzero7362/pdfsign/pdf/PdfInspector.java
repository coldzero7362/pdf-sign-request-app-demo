package io.github.coldzero7362.pdfsign.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 업로드한 파일이 정상 PDF인지 확인하고 페이지 정보를 뽑는다.
 * 악성 PDF로 인한 메모리 고갈을 막기 위해 파싱 버퍼는 임시 파일만 쓰고, 페이지 수를 제한한다.
 */
@Component
public class PdfInspector {

    public static final int MAX_PAGES = 50;
    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public List<PdfPageInfo> inspect(byte[] content) {
        if (!startsWithMagic(content)) {
            throw bad("PDF 파일이 아닙니다.");
        }
        try (PDDocument pdf = Loader.loadPDF(content, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            if (pdf.isEncrypted()) {
                throw bad("암호가 걸리거나 편집이 제한된 PDF는 사용할 수 없습니다.");
            }
            int count = pdf.getNumberOfPages();
            if (count < 1) {
                throw bad("페이지가 없는 PDF입니다.");
            }
            if (count > MAX_PAGES) {
                throw bad("페이지는 최대 " + MAX_PAGES + "장까지 올릴 수 있습니다.");
            }
            List<PdfPageInfo> pages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                PDPage page = pdf.getPage(i);
                PDRectangle box = page.getCropBox();
                int rotation = Math.floorMod(page.getRotation(), 360);
                boolean swap = rotation == 90 || rotation == 270;
                double w = swap ? box.getHeight() : box.getWidth();
                double h = swap ? box.getWidth() : box.getHeight();
                pages.add(new PdfPageInfo(i + 1, round(w), round(h), rotation));
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw bad("암호가 걸린 PDF는 사용할 수 없습니다.");
        } catch (IOException e) {
            throw bad("PDF를 읽을 수 없습니다. 파일이 손상되었을 수 있습니다.");
        }
    }

    private static boolean startsWithMagic(byte[] content) {
        // 표준상 %PDF- 앞에 잡음이 조금 있을 수 있어 앞 1KB 안에서 찾는다
        int limit = Math.min(content.length - MAGIC.length, 1024);
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < MAGIC.length; j++) {
                if (content[i + j] != MAGIC[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
