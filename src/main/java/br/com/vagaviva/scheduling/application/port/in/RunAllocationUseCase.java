package br.com.vagaviva.scheduling.application.port.in;

/** RF-20: aloca a fila nas vagas disponíveis (job, evento de publicação ou execução manual). */
public interface RunAllocationUseCase {

    AllocationRunResult run();

    /**
     * @param examinedSlots vagas travadas e avaliadas nesta execução
     * @param allocated agendamentos criados
     * @param withoutCandidate vagas sem ninguém elegível na fila (continuam disponíveis)
     */
    record AllocationRunResult(int examinedSlots, int allocated, int withoutCandidate) {
    }
}
