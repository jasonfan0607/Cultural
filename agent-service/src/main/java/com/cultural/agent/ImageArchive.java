package com.cultural.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;

/** Stores provider image bytes, never accepts a client-supplied download URL. */
@RestController
@RequestMapping("/api/agent/images")
public class ImageArchive {
    private final Path directory;
    public ImageArchive(@Value("${agent.image-directory:./data/generated-images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public String save(String base64) {
        try {
            if (base64 == null || base64.isBlank() || base64.length() > 48_000_000)
                throw new AgentException(502, "图片内容为空或过大");
            byte[] bytes = Base64.getDecoder().decode(base64);
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new AgentException(502, "图片服务返回了无效图片");
            String extension;
            if (bytes.length > 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71) extension = ".png";
            else if (bytes.length > 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) extension = ".jpg";
            else throw new AgentException(502, "图片格式暂不支持保存");
            Files.createDirectories(directory);
            String name = UUID.randomUUID() + extension;
            Path temporary = Files.createTempFile(directory, "incoming-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
            return "/agent-api/images/" + name;
        } catch (AgentException e) { throw e; }
        catch (Exception e) { throw new AgentException(502, "图片生成后保存失败，请检查服务器磁盘空间；不要自动重复生成"); }
    }
    @GetMapping("/{name}")
    public ResponseEntity<FileSystemResource> read(@PathVariable String name) {
        if (!name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)"))
            return ResponseEntity.notFound().build();
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePrivate().immutable())
                .body(new FileSystemResource(file));
    }
}
