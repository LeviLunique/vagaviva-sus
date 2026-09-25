/**
 * Kernel compartilhado: configurações transversais (segurança, OpenAPI, relógio) e tipos
 * comuns a todos os módulos. É o único módulo "aberto" do monólito modular.
 */
@ApplicationModule(displayName = "Shared Kernel", type = ApplicationModule.Type.OPEN)
package br.com.vagaviva.shared;

import org.springframework.modulith.ApplicationModule;
