package cn.edu.sar.web.system;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.common.web.GlobalExceptionHandler;
import cn.edu.sar.common.web.RequestIdFilter;
import cn.edu.sar.domain.system.SystemStatusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemControllerTest {
    private SystemStatusService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(SystemStatusService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SystemController(service), new ErrorFixture())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void returnsEnvelopeAndFreshRequestId() throws Exception {
        when(service.status()).thenReturn(new SystemStatusService.Status("UP", "CONNECTED", "8.0.46", "0.1.0", Instant.EPOCH));
        var first = mvc.perform(get("/api/v1/system/status").header("X-Request-Id", "untrusted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.database").value("CONNECTED"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String id = first.getResponse().getHeader("X-Request-Id");
        assertThat(id).matches("[0-9a-f-]{36}").isNotEqualTo("untrusted");
        mvc.perform(get("/api/v1/system/status")).andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void databaseFailureReturns503WithoutConnectionDetails() throws Exception {
        when(service.status()).thenThrow(new DataAccessResourceFailureException("jdbc:mysql://secret-host password=secret"));
        var result = mvc.perform(get("/api/v1/system/status")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret", "jdbc:", "stackTrace");
    }

    @Test
    void validationAndMalformedJsonHaveUnifiedErrors() throws Exception {
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.fields.name").exists());
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void businessAndUnexpectedErrorsHaveUnifiedEnvelope() throws Exception {
        mvc.perform(get("/test/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        var result = mvc.perform(get("/test/failure")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private details");
    }

    // These routes exist only inside the test fixture; they are never registered in the application.
    @RestController
    static class ErrorFixture {
        record Input(@NotBlank String name) {}
        @PostMapping("/test/validation") Input input(@RequestBody @Valid Input value) { return value; }
        @GetMapping("/test/conflict") void conflict() { throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "状态已变化，请刷新"); }
        @GetMapping("/test/failure") void failure() { throw new IllegalStateException("private details"); }
    }
}
