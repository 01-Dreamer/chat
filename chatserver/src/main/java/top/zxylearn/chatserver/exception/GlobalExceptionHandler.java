package top.zxylearn.chatserver.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.zxylearn.chatserver.common.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);
        String message = fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLoginException() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "登录状态已失效，请重新登录"));
    }

    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleSaTokenException() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("AUTH_ERROR", "身份认证失败"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_CONFLICT", "数据已存在或发生冲突"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "服务器暂时无法处理请求"));
    }
}
