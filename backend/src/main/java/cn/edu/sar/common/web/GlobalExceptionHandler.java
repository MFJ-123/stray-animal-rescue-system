package cn.edu.sar.common.web;

import cn.edu.sar.common.api.ApiResponse;
import cn.edu.sar.common.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> business(BusinessException exception, HttpServletRequest request) {
        return reply(exception.status(), exception.code(), exception.getMessage(), exception.data(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return reply(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请检查填写内容", Map.of("fields", fields), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid(Exception exception, HttpServletRequest request) {
        return reply(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请求参数格式不正确", null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> missing(NoResourceFoundException exception, HttpServletRequest request) {
        return reply(HttpStatus.NOT_FOUND, "NOT_FOUND", "访问的内容不存在", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return reply(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "该接口不支持此请求方式", null, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> database(DataAccessException exception, HttpServletRequest request) {
        log.warn("Database request failed: {}", exception.getClass().getSimpleName());
        return reply(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "服务暂时不可用，请稍后重试", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Request failed: {}", exception.getClass().getSimpleName());
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "处理失败，请提供请求号联系维护人员", null, request);
    }

    private ResponseEntity<?> reply(HttpStatus status, String code, String message, Object data, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, data, request));
    }
}
