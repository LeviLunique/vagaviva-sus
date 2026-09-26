package br.com.vagaviva.insights.application.service;

import br.com.vagaviva.shared.domain.BusinessRuleException;

final class InsightsErrors {

    private InsightsErrors() {
    }

    static BusinessRuleException invalidPeriod() {
        return new BusinessRuleException("INVALID_PERIOD", "A data inicial deve ser anterior ou igual à final.");
    }

    static BusinessRuleException periodTooLong() {
        return new BusinessRuleException("PERIOD_TOO_LONG", "O período pode ter no máximo 366 dias.");
    }
}
