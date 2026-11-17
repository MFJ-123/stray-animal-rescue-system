package cn.edu.sar.common.api;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Object data;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(HttpStatus status, String code, String message, Object data) {
        super(message);
        if (!status.is4xxClientError() && status != HttpStatus.SERVICE_UNAVAILABLE) {
            throw new IllegalArgumentException("受控异常必须使用 4xx 或依赖不可用503状态");
        }
        this.status = status;
        this.code = code;
        this.data = data;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Object data() { return data; }
}
