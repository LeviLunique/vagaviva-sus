/**
 * Trilha de auditoria (RF-05, LGPD): quem fez o quê, quando, em qual recurso, com qual
 * resultado e de qual IP. Outros módulos gravam pela API {@link br.com.vagaviva.audit.AuditTrail}.
 */
@ApplicationModule(displayName = "Auditoria")
package br.com.vagaviva.audit;

import org.springframework.modulith.ApplicationModule;
