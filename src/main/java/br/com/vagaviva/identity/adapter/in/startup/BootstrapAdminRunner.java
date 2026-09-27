package br.com.vagaviva.identity.adapter.in.startup;

import br.com.vagaviva.identity.application.port.in.BootstrapAdminUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** RF-03: ao subir a aplicação, garante o administrador inicial. */
@Component
class BootstrapAdminRunner implements ApplicationRunner {

    private final BootstrapAdminUseCase bootstrapAdmin;

    BootstrapAdminRunner(BootstrapAdminUseCase bootstrapAdmin) {
        this.bootstrapAdmin = bootstrapAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapAdmin.ensureAdminExists();
    }
}
