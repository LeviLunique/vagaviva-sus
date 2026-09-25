package br.com.vagaviva.shared.domain;

/** Regra de negócio ou de validação violada (422). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
