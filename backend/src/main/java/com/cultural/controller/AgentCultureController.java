package com.cultural.controller;

import com.cultural.common.response.Result;
import com.cultural.security.LoginUser;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.HtmlUtils;
import java.util.*;

/** Authenticated, read-only projection of published cultural records. No arbitrary SQL tool. */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Validated
public class AgentCultureController {
    private final JdbcTemplate jdbc;

    @GetMapping("/identity")
    public Result<Map<String, Long>> identity(@AuthenticationPrincipal LoginUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return Result.success(Map.of("userId", user.getUserId()));
    }

    @GetMapping("/culture")
    public Result<List<Map<String, Object>>> search(
            @RequestParam @NotBlank @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "all") @Pattern(regexp = "all|heritage|art") String type,
            @RequestParam(defaultValue = "") @Size(max = 50) String city) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!type.equals("art")) results.addAll(searchTable("heritage", "heritage", keyword, city));
        if (!type.equals("heritage")) results.addAll(searchTable("traditional_art", "art", keyword, city));
        return Result.success(results);
    }

    private List<Map<String, Object>> searchTable(String table, String type, String keyword, String city) {
        // table/type are hardcoded by the two branches above; all user values are bound parameters.
        // Historical seed copies share a name/city. Deduplicate before applying the result limit.
        String sql = "SELECT MIN(id) AS id, name AS title, MAX(description) AS description, city, MAX(category) AS category FROM " + table
                + " WHERE status=1 AND deleted=0 AND (name LIKE ? OR alias LIKE ? OR description LIKE ? OR content LIKE ?)"
                + " AND (? = '' OR city = ?) GROUP BY name, city ORDER BY MAX(view_count) DESC, MIN(id) LIMIT 5";
        String term = "%" + keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        sql = sql.replace("LIKE ?", "LIKE ? ESCAPE '!'");
        List<Map<String, Object>> rows = jdbc.queryForList(sql, term, term, term, term, city, city);
        rows.forEach(row -> row.put("type", type));
        return rows;
    }

    @GetMapping("/culture/{type}/{id}")
    public Result<Map<String, Object>> detail(
            @PathVariable @Pattern(regexp = "heritage|art") String type,
            @PathVariable @Positive Long id) {
        String table = type.equals("heritage") ? "heritage" : "traditional_art";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name AS title, description, city, category, LEFT(content, 12000) AS content FROM "
                        + table + " WHERE id=? AND status=1 AND deleted=0", id);
        if (rows.isEmpty()) return Result.notFound("资料不存在或未公开");
        Map<String, Object> row = rows.get(0);
        row.put("type", type);
        String content = Objects.toString(row.get("content"), "")
                .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|h[1-6]|li|ul|ol|section)\\s*>", "\n")
                .replaceAll("<[^>]*>", "");
        row.put("content", HtmlUtils.htmlUnescape(content).replace('\u00a0', ' ')
                .replaceAll("[ \\t]+", " ").replaceAll("\\n[ \\t]*\\n+", "\n\n").trim());
        return Result.success(row);
    }
}
