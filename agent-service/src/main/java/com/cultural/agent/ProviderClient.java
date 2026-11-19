package com.cultural.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Fixed upstream origins, no redirects, no credentials or provider response bodies in errors/logs. */
@Component
public class ProviderClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    private final AgentSettings settings;
    private final ImageArchive archive;
    public ProviderClient(AgentSettings settings, ImageArchive archive) { this.settings = settings; this.archive = archive; }

    public long authenticate(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() > 8192)
            throw new AgentException(401, "请先登录后使用智能体");
        JsonNode data = backend("/agent/identity", bearer);
        long user = data.path("userId").asLong(0);
        if (user <= 0) throw new AgentException(401, "登录已失效，请重新登录");
        return user;
    }

    public JsonNode backend(String path, String bearer) {
        JsonNode result = send(HttpRequest.newBuilder(URI.create(settings.backendUrl() + path))
                .header("Authorization", bearer).timeout(Duration.ofSeconds(12)).GET().build(), "文化资料服务");
        if (result.path("code").asInt() != 200) {
            int code = result.path("code").asInt();
            if (code == 401 || code == 403) throw new AgentException(401, "登录已失效，请重新登录");
            if (code == 404) throw new AgentException(404, "资料不存在或未公开");
            throw new AgentException(502, "文化资料查询失败，请检查后端和数据库");
        }
        return result.path("data");
    }

    public String image(String prompt, String size) {
        if (!settings.imageReady()) throw new AgentException(503, "尚未配置服务端 VOLCENGINE_API_KEY，暂不能生成图片");
        if (prompt == null || prompt.isBlank() || prompt.length() > 4000)
            throw new AgentException(400, "图片描述应为 1～4000 字");
        if (!java.util.Set.of("2048x2048", "1920x1920", "2560x1440", "1440x2560", "1920x1088", "1088x1920", "2304x1728", "1728x2304").contains(size))
            throw new AgentException(400, "不支持的图片尺寸");
        try {
            String body = json.writeValueAsString(Map.of("model", settings.imageModel(), "prompt", prompt,
                    "size", size, "watermark", true, "response_format", "b64_json"));
            JsonNode result = send(HttpRequest.newBuilder(URI.create(settings.imageUrl() + "/images/generations"))
                    .header("Authorization", "Bearer " + settings.imageKey()).header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(115)).POST(HttpRequest.BodyPublishers.ofString(body)).build(), "图片服务");
            return archive.save(result.path("data").path(0).path("b64_json").asText());
        } catch (AgentException e) { throw e; }
        catch (Exception e) { throw new AgentException(502, "图片服务响应无法读取"); }
    }

    private JsonNode send(HttpRequest request, String label) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                if (label.equals("文化资料服务")) throw new AgentException(401, "登录已失效，请重新登录");
                throw new AgentException(502, label + "密钥无效或没有模型权限，请检查服务端配置");
            }
            if (status == 429) throw new AgentException(429, label + "繁忙或额度不足，请稍后再试");
            if (status < 200 || status >= 300) throw new AgentException(502, label + "暂时不可用");
            if (response.body().length() > (label.equals("图片服务") ? 64_000_000 : 500000)) throw new AgentException(502, label + "响应过大");
            return json.readTree(response.body());
        } catch (AgentException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AgentException(499, "请求已取消"); }
        catch (Exception e) { throw new AgentException(502, label + "连接失败或超时，请稍后重试"); }
    }
}
