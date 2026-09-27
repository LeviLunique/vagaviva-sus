package br.com.vagaviva;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** RF-11: o perfil local carrega o seed de demonstração e cria os usuários de cada papel. */
@SpringBootTest
@ActiveProfiles({"test", "local"})
@Import(TestcontainersConfiguration.class)
class SeedIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("seed: ≥ 4 UBS, 3 executantes, 8 especialidades (1 sensível) e 30 pacientes fictícios")
    void shouldLoadDemoCatalogAndPatients() {
        assertThat(count("select count(*) from health_unit where type = 'PRIMARY_CARE'")).isGreaterThanOrEqualTo(4);
        assertThat(count("select count(*) from health_unit where type = 'SPECIALIZED'")).isGreaterThanOrEqualTo(3);
        assertThat(count("select count(*) from specialty")).isGreaterThanOrEqualTo(8);
        assertThat(count("select count(*) from specialty where sensitive")).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from patient where phone like '+55119999900%'")).isEqualTo(30);
    }

    @Test
    @DisplayName("usuários de demonstração: um por papel, com unidades compatíveis (RN-03)")
    void shouldCreateDemoUsers() {
        assertThat(jdbc.queryForList("select role from staff_user where email like '%@vagaviva.local'", String.class))
                .contains("REQUESTER", "REGULATOR", "SCHEDULER", "MANAGER");
        assertThat(count("""
                select count(*) from staff_user u join health_unit h on h.id = u.health_unit_id
                where (u.role = 'REQUESTER' and h.type = 'PRIMARY_CARE') or (u.role = 'SCHEDULER' and h.type = 'SPECIALIZED')
                """)).isEqualTo(2);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
