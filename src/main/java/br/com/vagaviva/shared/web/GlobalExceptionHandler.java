package br.com.vagaviva.shared.web;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.DomainException;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.domain.UnauthenticatedException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.TypeMismatchException;

/**
 * Traduz exceções em {@code application/problem+json} (RFC 9457) com {@code code} estável,
 * {@code traceId} e, em validação, a lista {@code errors}. Nunca expõe stack trace, SQL ou dado
 * pessoal: exceções inesperadas viram 500 genérico (detalhes só no log).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<Class<? extends DomainException>, ErrorCode> FAMILIES = Map.of(
            NotFoundException.class, ErrorCode.NOT_FOUND,
            ConflictException.class, ErrorCode.CONFLICT,
            BusinessRuleException.class, ErrorCode.BUSINESS_RULE_VIOLATED,
            GoneException.class, ErrorCode.GONE,
            ForbiddenOperationException.class, ErrorCode.ACCESS_DENIED,
            UnauthenticatedException.class, ErrorCode.UNAUTHENTICATED);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        ErrorCode family = FAMILIES.getOrDefault(ex.getClass(), familyOf(ex));
        ProblemDetail body = problem(family, ex.code(), ex.getMessage());
        return ResponseEntity.status(family.status()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(ErrorCode.UNAUTHENTICATED, "Autenticação necessária: envie um token Bearer válido."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(ErrorCode.ACCESS_DENIED, "Seu perfil não tem permissão para esta operação."));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentModification(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(ErrorCode.CONFLICT, "CONCURRENT_MODIFICATION",
                "O registro foi alterado por outra operação. Recarregue e tente novamente."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        return ResponseEntity.internalServerError()
                .body(problem(ErrorCode.INTERNAL_ERROR, "Ocorreu um erro inesperado. Tente novamente mais tarde."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return validationFailed(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(result.getMethodParameter().getParameterName(),
                                messageOf(error))))
                .toList();
        return validationFailed(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(ErrorCode.MALFORMED_REQUEST,
                "O corpo da requisição não é um JSON válido ou tem campos com formato incorreto."));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(ErrorCode.MALFORMED_REQUEST,
                "Parâmetro '%s' com valor inválido.".formatted(ex.getPropertyName())));
    }

    /** Demais exceções do Spring MVC (404 de rota, 405, 415…): mesmo formato, título em português. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail detail && detail.getProperties() == null) {
            ErrorCode code = ErrorCode.fromStatus(statusCode.value());
            detail.setType(code.type());
            detail.setTitle(code.title());
            enrich(detail, code.name());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationFailed(List<FieldViolation> errors) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Um ou mais campos são inválidos.");
        body.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        return problem(code, code.name(), detail);
    }

    private static ProblemDetail problem(ErrorCode family, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(family.status()), detail);
        body.setType(ErrorCode.typeOf(code));
        body.setTitle(family.title());
        enrich(body, code);
        return body;
    }

    private static void enrich(ProblemDetail body, String code) {
        body.setProperty("code", code);
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            body.setProperty("traceId", traceId);
        }
    }

    /** Subclasses criadas pelos módulos (ex.: {@code InvalidCredentialsException}) herdam a família do pai. */
    private static ErrorCode familyOf(DomainException ex) {
        return FAMILIES.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(ex))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(ErrorCode.BUSINESS_RULE_VIOLATED);
    }

    private static String messageOf(MessageSourceResolvable error) {
        return error.getDefaultMessage();
    }

    public record FieldViolation(String field, String message) {
    }
}
