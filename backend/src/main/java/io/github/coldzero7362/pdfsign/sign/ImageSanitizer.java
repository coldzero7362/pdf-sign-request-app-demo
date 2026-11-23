package io.github.coldzero7362.pdfsign.sign;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 서명·도장 이미지를 검사하고 PNG로 다시 그린다.
 * - PNG, JPEG만 받는다 (실제 내용으로 판별).
 * - 해독 전에 가로·세로를 먼저 읽어 거대한 이미지(압축 폭탄)를 거른다.
 * - 다시 그려 저장하므로 메타데이터와 숨은 데이터가 사라진다.
 */
@Component
public class ImageSanitizer {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_SIDE = 3000;
    static final int OUT_MAX_SIDE = 1200;

    public byte[] toPng(byte[] input) {
        if (input == null || input.length == 0) {
            throw bad("이미지가 비어 있습니다.");
        }
        if (input.length > MAX_BYTES) {
            throw bad("이미지는 2MB 이하만 올릴 수 있습니다.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw bad("PNG 또는 JPEG 이미지만 올릴 수 있습니다.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw bad("PNG 또는 JPEG 이미지만 올릴 수 있습니다.");
                }
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE) {
                    throw bad("이미지는 가로·세로 " + MAX_SIDE + "px 이하만 올릴 수 있습니다.");
                }
                BufferedImage src = reader.read(0);
                return redraw(src);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw bad("이미지를 읽을 수 없습니다.");
        }
    }

    private static byte[] redraw(BufferedImage src) throws IOException {
        double scale = Math.min(1.0, (double) OUT_MAX_SIDE / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return bytes.toByteArray();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
