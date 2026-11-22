package com.example.aicustomerservice.exception;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.security.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> handleNotLogin(NotLoginException e) {
        return ApiResponse.error(401, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.error(400, "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResponse.error(400, "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ApiResponse<Void> handleOptimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ApiResponse.error(409, "操作冲突：该记录已被其他请求修改，请刷新后重试");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ApiResponse.error(404, "接口或资源不存在: " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error("系统异常，请稍后重试");
    }
}