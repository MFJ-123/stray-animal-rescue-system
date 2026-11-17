package cn.edu.sar.web.system;

import cn.edu.sar.common.api.ApiResponse;
import cn.edu.sar.common.web.PublicRead;
import cn.edu.sar.domain.system.SystemStatusService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final SystemStatusService service;

    public SystemController(SystemStatusService service) { this.service = service; }

    @PublicRead @GetMapping("/status")
    public ResponseEntity<ApiResponse<SystemStatusService.Status>> status(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.status(), request));
    }
}
