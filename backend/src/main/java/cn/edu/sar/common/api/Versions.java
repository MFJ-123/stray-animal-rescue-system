package cn.edu.sar.common.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

public final class Versions {
    private Versions() {}
    public static void require(long expected, long actual) {
        if (expected < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "版本号不能为负数");
        if (expected != actual) throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
            "资料已更新，请刷新后确认再提交", Map.of("currentVersion", actual));
    }
}
