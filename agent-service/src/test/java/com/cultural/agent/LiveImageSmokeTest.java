package com.cultural.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: one paid image using the exact service used by the design page. */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_IMAGE", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiveImageSmokeTest {
    @Autowired ImageService images;
    @Autowired AgentSettings settings;
    @Test void generatesAndDownloadsOneRealImage() throws Exception {
        assertTrue(settings.imageReady(), "Server image credential must be configured");
        String requestId = UUID.randomUUID().toString();
        String prompt = "南京云锦主题茶叶礼盒设计效果图，现代简约，米白色礼盒，精细金色云纹与深绿色点缀，三分之四视角，干净浅色背景，真实纸张材质，精致摄影，无需文字";
        long start = System.nanoTime();
        String url = images.generate(987654321, requestId, prompt, "2048x2048");
        // Verify replay without making a second billed request.
        assertEquals(url, images.generate(987654321, requestId, prompt, "2048x2048"));
        var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build().send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        var image = ImageIO.read(new ByteArrayInputStream(response.body()));
        assertNotNull(image, "Provider URL must contain a decodable image");
        assertEquals(2048, image.getWidth()); assertEquals(2048, image.getHeight());
        Path directory = Path.of("tests", "artifacts"); Files.createDirectories(directory);
        Path output = directory.resolve("seedream-5.5-test.png");
        assertTrue(ImageIO.write(image, "png", output.toFile()));
        System.out.printf("LIVE IMAGE PASS: model=%s size=%dx%d bytes=%d durationMs=%d output=%s%n",
                settings.imageModel(), image.getWidth(), image.getHeight(), response.body().length,
                (System.nanoTime()-start)/1_000_000, output.toAbsolutePath());
    }
}
