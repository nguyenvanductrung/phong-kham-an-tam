package vn.phongkham.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ProblemDetailsAdvice {
    @ExceptionHandler(ApiException.class)
    ProblemDetail handle(ApiException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        detail.setType(URI.create("urn:clinic:error:" + ex.errorCode().toLowerCase()));
        detail.setTitle(ex.status().getReasonPhrase());
        detail.setProperty("errorCode", ex.errorCode());
        detail.setProperty("traceId", traceId(request));
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra lại thông tin.");
        detail.setType(URI.create("urn:clinic:error:validation_failed"));
        detail.setTitle("Validation failed");
        detail.setProperty("errorCode", "VALIDATION_FAILED");
        detail.setProperty("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage(), (a, b) -> a)));
        detail.setProperty("traceId", traceId(request));
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String sqlState = sqlState(ex);
        HttpStatus status = "23P01".equals(sqlState) || "23505".equals(sqlState)
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        String errorCode = "23P01".equals(sqlState) ? "APPOINTMENT_CONFLICT"
                : "23505".equals(sqlState) ? "RESOURCE_CONFLICT" : "DATA_CONSTRAINT_VIOLATION";
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status,
                "Dữ liệu xung đột với trạng thái hiện tại hoặc một ràng buộc nghiệp vụ.");
        detail.setType(URI.create("urn:clinic:error:" + errorCode.toLowerCase()));
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("errorCode", errorCode);
        detail.setProperty("traceId", traceId(request));
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá giới hạn cho phép.");
        detail.setType(URI.create("urn:clinic:error:file_too_large"));
        detail.setTitle(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
        detail.setProperty("errorCode", "FILE_TOO_LARGE");
        detail.setProperty("traceId", traceId(request));
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Có lỗi hệ thống. Vui lòng thử lại.");
        detail.setType(URI.create("urn:clinic:error:internal_error"));
        detail.setTitle("Internal Server Error");
        detail.setProperty("errorCode", "INTERNAL_ERROR");
        detail.setProperty("traceId", traceId(request));
        return detail;
    }

    private String traceId(HttpServletRequest request) {
        Object id = request.getAttribute(CorrelationIdFilter.HEADER);
        return id == null ? "unknown" : id.toString();
    }

    private static String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) return sqlException.getSQLState();
        }
        return null;
    }
}

