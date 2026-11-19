package com.cultural.agent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    // A disconnected SSE response cannot carry a second JSON error body.
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void disconnected(AsyncRequestNotUsableException e) { }
    @ExceptionHandler(AgentException.class)
    ResponseEntity<?> known(AgentException e) { return ResponseEntity.status(e.status()).body(Map.of("code", e.status(), "message", e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请求格式错误，请检查输入长度和请求编号")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e, HttpServletResponse response) {
        if (response.isCommitted()) return null;
        return ResponseEntity.internalServerError().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("code", 500, "message", "服务暂不可用，请稍后重试"));
    }
}
