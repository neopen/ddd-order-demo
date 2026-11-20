package com.neopen.ddd.infrastructure.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 (Global Exception Handler)。
 *
 * <p>分层职责：接口层的横切组件，把各类异常转换为统一的 JSON 错误响应，
 * 避免技术细节泄漏给调用方，也避免异常堆栈直接暴露。</p>
 *
 * <p>状态码映射：参数校验失败与非法参数映射为 400；状态冲突与乐观锁失败映射为 409；
 * 其他未预期异常映射为 500。</p>
 *
 * <p>响应体结构：timestamp、status、code、message；message 为空时回退为
 * HTTP 标准原因短语，保证调用方总能拿到可读信息。</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理请求体参数校验失败，汇总所有字段错误为一条消息。
     *
     * @param ex Spring MVC 参数校验异常
     * @return 400 响应，code 为 VALIDATION_ERROR
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /**
     * 处理方法参数或路径参数上的约束校验失败。
     *
     * @param ex 约束校验异常
     * @return 400 响应，code 为 VALIDATION_ERROR
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    /**
     * 处理非法参数，通常来自领域层或应用层的入参校验。
     *
     * @param ex 非法参数异常
     * @return 400 响应，code 为 BAD_REQUEST
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    /**
     * 处理非法状态，表示当前订单状态不允许该操作。
     *
     * @param ex 非法状态异常
     * @return 409 响应，code 为 STATE_CONFLICT
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return response(HttpStatus.CONFLICT, "STATE_CONFLICT", ex.getMessage());
    }

    /**
     * 处理乐观锁冲突，提示调用方刷新后重试。
     *
     * <p>不对原始异常信息做透出，避免暴露数据库细节。</p>
     *
     * @param ex 乐观锁异常
     * @return 409 响应，code 为 CONCURRENT_MODIFICATION
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "订单已被其他请求修改，请刷新后重试");
    }

    /**
     * 兜底处理未预期异常，记录错误日志但对外隐藏细节。
     *
     * @param ex 未被其他处理器命中的服务端异常
     * @return 500 响应，code 为 INTERNAL_ERROR
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("未处理的服务端异常", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试");
    }

    /**
     * 构造统一错误响应体。
     *
     * @param status HTTP 状态码
     * @param code 业务错误码，供调用方按类别处理
     * @param message 提示信息，为空时使用状态码的标准原因短语
     * @return 包含错误详情的 ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        // message 缺失时退回 HTTP 标准原因短语，避免返回 null
        body.put("message", message == null ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
