package com.finpulse.exception;


import com.finpulse.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionalHandler {

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFile(
            InvalidFileException ex,
            HttpServletRequest request) {

        log.warn("Invalid file. path={}, reason={}",
                request.getRequestURI(),
                ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn("Invalid parameter. path={}, param={}, value={}",
                request.getRequestURI(), ex.getName(), ex.getValue());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        "Invalid parameter '" + ex.getName() +
                                "'. Expected format: 2024-01-15T10:30:00",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing parameter. path={}, param={}",
                request.getRequestURI(), ex.getParameterName());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        "Required parameter '" + ex.getParameterName() + "' is missing",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        log.warn("Upload size exceeded. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(
                        "File size exceeds the maximum allowed limit",
                        request.getRequestURI()
                ));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error. path={}", request.getRequestURI(), ex);

        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(
                        "Something went wrong. Please try again later.",
                        request.getRequestURI()
                ));
    }

}
