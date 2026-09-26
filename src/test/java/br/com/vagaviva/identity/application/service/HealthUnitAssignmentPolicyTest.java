package br.com.vagaviva.identity.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthUnitAssignmentPolicyTest {

    private static final UUID UNIT = UUID.randomUUID();

    @Mock CatalogApi catalog;

    private HealthUnitAssignmentPolicy policy() {
        return new HealthUnitAssignmentPolicy(catalog);
    }

    private void unitIs(HealthUnitType type, boolean active) {
        when(catalog.findUnit(UNIT)).thenReturn(Optional.of(
                new HealthUnitSummary(UNIT, "1234567", "Unidade", type, "3550308", "São Paulo", "Rua 1", null, active)));
    }

    @ParameterizedTest(name = "{0} em {1} ⇒ permitido")
    @CsvSource({"REQUESTER, PRIMARY_CARE", "SCHEDULER, SPECIALIZED", "REGULATOR, SPECIALIZED", "MANAGER, PRIMARY_CARE"})
    @DisplayName("RN-03: papel compatível com o tipo de unidade (papéis sem exigência aceitam qualquer tipo)")
    void shouldAllowCompatibleUnit(Role role, HealthUnitType type) {
        unitIs(type, true);

        assertThatCode(() -> policy().check(role, UNIT)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} em {1} ⇒ HEALTH_UNIT_TYPE_MISMATCH")
    @CsvSource({"REQUESTER, SPECIALIZED", "SCHEDULER, PRIMARY_CARE"})
    @DisplayName("RN-03: REQUESTER só em UBS e SCHEDULER só em unidade executante")
    void shouldRejectIncompatibleUnit(Role role, HealthUnitType type) {
        unitIs(type, true);

        assertThatThrownBy(() -> policy().check(role, UNIT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("HEALTH_UNIT_TYPE_MISMATCH");
    }

    @Test
    @DisplayName("unidade inexistente ⇒ HEALTH_UNIT_NOT_FOUND; inativa ⇒ HEALTH_UNIT_INACTIVE (422)")
    void shouldRejectMissingOrInactiveUnit() {
        when(catalog.findUnit(UNIT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policy().check(Role.REQUESTER, UNIT)).extracting("code").isEqualTo("HEALTH_UNIT_NOT_FOUND");

        unitIs(HealthUnitType.PRIMARY_CARE, false);
        assertThatThrownBy(() -> policy().check(Role.REQUESTER, UNIT)).extracting("code").isEqualTo("HEALTH_UNIT_INACTIVE");
    }

    @Test
    @DisplayName("sem unidade não há o que validar (a obrigatoriedade é regra do domínio)")
    void shouldSkipWhenNoUnit() {
        assertThatCode(() -> policy().check(Role.REGULATOR, null)).doesNotThrowAnyException();
        verifyNoInteractions(catalog);
    }
}
