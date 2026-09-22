package com.exam.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 拼接 MDC 上下文（traceId/userId/uri）到日志消息，弥补默认日志 pattern 不含 %X。 */
    private static String ctx() {
        String t = MDC.get("traceId");
        String u = MDC.get("userId");
        String uri = MDC.get("uri");
        String s = "";
        if (t != null) s += " traceId=" + t;
        if (u != null) s += " uid=" + u;
        if (uri != null) s += " uri=" + uri;
        return s;
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        log.warn("业务异常{}: {}", ctx(), e.getMessage());
        Integer code = e.getCode();
        HttpStatus status = code == null ? HttpStatus.BAD_REQUEST : switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 423 -> HttpStatus.LOCKED;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(R.error(code == null ? 400 : code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败{}: {}", ctx(), msg);
        return R.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常{}", ctx(), e);
        return R.error(500, "系统内部错误，请稍后重试");
    }
}
