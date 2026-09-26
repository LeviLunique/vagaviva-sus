/**
 * Reaproveitamento de vagas (RF-31 a RF-34): vaga liberada perto do atendimento vira oferta de
 * encaixe em rodadas para quem aceita encaixe; o primeiro aceite vence (atualização atômica da
 * vaga). Expõe {@link br.com.vagaviva.reallocation.ReallocationApi} para o link do paciente.
 */
@ApplicationModule(displayName = "Reaproveitamento de vagas")
package br.com.vagaviva.reallocation;

import org.springframework.modulith.ApplicationModule;
