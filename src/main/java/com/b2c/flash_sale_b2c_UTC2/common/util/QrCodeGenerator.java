package com.b2c.flash_sale_b2c_UTC2.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class QrCodeGenerator {

    private QrCodeGenerator() {}

    /**
     * Tạo mã QR dưới dạng Data URL (data:image/png;base64,...).
     *
     * @param text   Nội dung cần mã hóa (URL hoặc payload ZaloPay)
     * @param width  Chiều rộng (pixel)
     * @param height Chiều cao (pixel)
     * @return Chuỗi Data URL PNG base64 hoặc null nếu text rỗng
     */
    public static String generateQrBase64(String text, int width, int height) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Lỗi tạo mã QR cho nội dung text={}: {}", text, e.getMessage(), e);
            return null;
        }
    }
}
