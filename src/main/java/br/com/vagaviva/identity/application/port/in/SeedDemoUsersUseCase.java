package br.com.vagaviva.identity.application.port.in;

/** RF-11: cria um profissional de demonstração por papel (somente com {@code vagaviva.demo.enabled=true}). */
public interface SeedDemoUsersUseCase {

    /** @return quantos usuários foram criados nesta chamada (0 se já existiam) */
    int seed();
}
