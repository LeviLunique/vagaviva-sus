package br.com.vagaviva.architecture;

import br.com.vagaviva.VagaVivaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Garante as fronteiras do monólito modular (sem ciclos, sem acesso a internals de outro módulo). */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(VagaVivaApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void generatesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
