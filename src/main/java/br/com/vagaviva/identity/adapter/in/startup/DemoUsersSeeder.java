package br.com.vagaviva.identity.adapter.in.startup;

import br.com.vagaviva.identity.application.port.in.SeedDemoUsersUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/** RF-11: nos perfis de demonstração (local e demo/hml), cria os usuários de cada papel no startup. */
@Component
@ConditionalOnBooleanProperty("vagaviva.demo.enabled")
class DemoUsersSeeder implements ApplicationRunner {

    private final SeedDemoUsersUseCase seedDemoUsers;

    DemoUsersSeeder(SeedDemoUsersUseCase seedDemoUsers) {
        this.seedDemoUsers = seedDemoUsers;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDemoUsers.seed();
    }
}
