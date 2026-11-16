package cn.edu.sar.common.api;

import jakarta.servlet.http.HttpServletRequest;
import cn.edu.sar.common.web.RequestIdFilter;

public record ApiResponse<T>(String code, String message, String requestId, T data) {
    public static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return new ApiResponse<>("OK", "操作成功", RequestIdFilter.get(request), data);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data, HttpServletRequest request) {
        return new ApiResponse<>(code, message, RequestIdFilter.get(request), data);
    }
}
