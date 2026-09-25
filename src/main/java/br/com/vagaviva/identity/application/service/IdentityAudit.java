package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.AuditTrail;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Ações de identidade auditadas (RF-05) — sempre sobre o recurso {@code STAFF_USER}. */
@Component
class IdentityAudit {

    static final String RESOURCE_TYPE = "STAFF_USER";
    static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    static final String LOGIN_FAILED = "LOGIN_FAILED";
    static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    static final String STAFF_USER_CREATED = "STAFF_USER_CREATED";
    static final String STAFF_USER_ACTIVATED = "STAFF_USER_ACTIVATED";
    static final String STAFF_USER_DEACTIVATED = "STAFF_USER_DEACTIVATED";

    private final AuditTrail auditTrail;

    IdentityAudit(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    /** Ação do próprio usuário (login): ele é o ator e o recurso. {@code user} nulo = e-mail desconhecido. */
    void login(@Nullable StaffUser user, String action, AuditOutcome outcome, @Nullable String clientIp,
            Map<String, String> details) {
        auditTrail.record(new AuditEntry(
                user == null ? null : user.id(),
                user == null ? null : user.role(),
                action, RESOURCE_TYPE,
                user == null ? null : user.id().toString(),
                outcome, clientIp, details));
    }

    /** Ação administrativa sobre um profissional; {@code actor} nulo = sistema (admin inicial). */
    void administration(@Nullable CurrentUser actor, StaffUser target, String action, @Nullable String clientIp,
            Map<String, String> details) {
        auditTrail.record(new AuditEntry(
                actor == null ? null : actor.id(),
                actor == null ? null : actor.role(),
                action, RESOURCE_TYPE, target.id().toString(),
                AuditOutcome.SUCCESS, clientIp, details));
    }
}
