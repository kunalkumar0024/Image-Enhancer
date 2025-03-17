package com.image.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@RestController
@CrossOrigin(origins = "*")
public class ImageEnhanceController {

    private static final Logger logger = LoggerFactory.getLogger(ImageEnhanceController.class);

    @PostMapping(value = "/enhance", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<byte[]>> enhanceImage(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(filePart -> {
            logger.info("Received file: {}", filePart.filename());

            return filePart.content()
                    .collect(ByteArrayOutputStream::new, (outputStream, buffer) -> {
                        try {
                            ByteBuffer byteBuffer = buffer.asByteBuffer();
                            byte[] bytes = new byte[byteBuffer.remaining()];
                            byteBuffer.get(bytes);
                            outputStream.write(bytes);
                        } catch (IOException e) {
                            logger.error("Error writing file to output stream", e);
                        }
                    })
                    .flatMap(outputStream -> processImage(outputStream.toByteArray(), filePart.filename()));
        });
    }

    private Mono<ResponseEntity<byte[]>> processImage(byte[] fileBytes, String filename) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
            BufferedImage originalImage = ImageIO.read(inputStream);

            if (originalImage == null) {
                logger.error("Failed to read image: {}", filename);
                return Mono.just(ResponseEntity.badRequest().body("Invalid image file!".getBytes()));
            }

            logger.info("Enhancing image: {}", filename);

            // Enhance image quality without affecting brightness
            BufferedImage enhancedImage = enhanceImageQuality(originalImage);

            // Convert to byte array
            byte[] enhancedImageBytes = convertToByteArray(enhancedImage, filename);

            return Mono.just(ResponseEntity.ok()
                    .contentType(getMediaType(filename))
                    .body(enhancedImageBytes));

        } catch (IOException e) {
            logger.error("Error processing image: {}", e.getMessage());
            return Mono.just(ResponseEntity.internalServerError().body("Error processing image!".getBytes()));
        }
    }

    private BufferedImage enhanceImageQuality(BufferedImage image) {
        logger.info("Applying image quality enhancement...");

        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImage.createGraphics();

        // Use rendering hints for quality enhancement
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Apply sharpening filter to improve details
        float[] sharpenKernel = {
                0.f, -1.f,  0.f,
                -1.f,  5.f, -1.f,
                0.f, -1.f,  0.f
        };
        Kernel kernel = new Kernel(3, 3, sharpenKernel);
        ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        newImage = convolveOp.filter(newImage, null);

        return newImage;
    }

    private byte[] convertToByteArray(BufferedImage image, String filename) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String format = filename.toLowerCase().endsWith(".png") ? "png" : "jpg";
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private MediaType getMediaType(String filename) {
        if (filename.toLowerCase().endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else {
            return MediaType.IMAGE_JPEG;
        }
    }
}
