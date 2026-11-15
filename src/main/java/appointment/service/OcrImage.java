package appointment.service;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Prepares an ID photo for OCR: scales it so the card's text is big enough for Tesseract (longer side about
 * 2000px), turns it grey, and evens out glare and shadows by dividing each pixel by the brightness around it.
 * Phone photos of IDs often have a bright reflection over part of the name, which otherwise cuts words short.
 */
final class OcrImage {

    private static final int TARGET = 2000;

    private OcrImage() { }

    /** The cleaned-up image as PNG bytes, or null when the photo can't be decoded here (e.g. WebP). */
    static byte[] clean(byte[] file) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(file));
            if (src == null || src.getWidth() < 20 || src.getHeight() < 20) return null;
            double scale = Math.min(3.0, (double) TARGET / Math.max(src.getWidth(), src.getHeight()));
            int w = (int) Math.round(src.getWidth() * scale), h = (int) Math.round(src.getHeight() * scale);

            BufferedImage grey = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            var g = grey.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            var raster = grey.getRaster();
            int[] px = raster.getPixels(0, 0, w, h, (int[]) null);
            int[] out = normalise(px, w, h, Math.max(15, (int) Math.round(Math.max(w, h) * 0.04)));
            raster.setPixels(0, 0, w, h, out);

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(grey, "png", png);
            return png.toByteArray();
        } catch (IOException | RuntimeException | LinkageError e) {   // no image support in this JRE: OCR the photo as is
            return null;
        }
    }

    /** Each pixel divided by the mean of the window around it (a summed-area table keeps this fast). */
    static int[] normalise(int[] px, int w, int h, int radius) {
        long[] sum = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long row = 0;
            for (int x = 0; x < w; x++) {
                row += px[y * w + x];
                sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
            }
        }
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius), y1 = Math.min(h, y + radius + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - radius), x1 = Math.min(w, x + radius + 1);
                long s = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0];
                double mean = (double) s / ((long) (y1 - y0) * (x1 - x0));
                out[y * w + x] = (int) Math.max(0, Math.min(255, Math.round(px[y * w + x] / (mean + 1e-3) * 235)));
            }
        }
        return out;
    }
}
