/**
 * Agenda das unidades executantes, alocação automática da fila (RF-20) e comparecimento. Recebe
 * pacientes da regulação pela {@code QueueApi}; expõe {@link br.com.vagaviva.scheduling.SchedulingApi}.
 */
@ApplicationModule(displayName = "Agenda e alocação")
package br.com.vagaviva.scheduling;

import org.springframework.modulith.ApplicationModule;
