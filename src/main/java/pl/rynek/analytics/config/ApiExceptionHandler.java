package pl.rynek.analytics.config;

import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Błędy RFC 7807 z treścią po polsku (§1). Zastępuje domyślny handler problemdetails; pozostałe wyjątki MVC,
 * w tym {@code ResponseStatusException} z serwisów, obsługuje klasa bazowa.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Pierwszy błąd pola trafia do {@code detail} (frontend pokazuje go pod formularzem), wszystkie do {@code errors}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Formularz zawiera błędy.");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        e -> e.getDefaultMessage() == null ? "" : e.getDefaultMessage(), (first, second) -> first)));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Serwis, z którego feed składa dane, nie odpowiada: 503 (gateway robi tak samo dla niedziałającego serwisu). */
    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<Object> downstreamDown(ResourceAccessException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Dane chwilowo niedostępne. Spróbuj ponownie za chwilę.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /** Błąd innego serwisu: 404 (np. oferta usunięta) przechodzi dalej, reszta to 502. */
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Object> downstreamError(RestClientResponseException ex, WebRequest request) {
        boolean notFound = ex.getStatusCode().value() == 404;
        HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        String detail = notFound ? "Nie znaleziono oferty." : "Dane chwilowo niedostępne. Spróbuj ponownie za chwilę.";
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), new HttpHeaders(), status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Nieprawidłowe dane żądania.");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }
}
