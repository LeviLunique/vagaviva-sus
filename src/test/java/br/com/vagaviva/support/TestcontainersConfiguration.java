package br.com.vagaviva.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infraestrutura real dos testes de integração: PostgreSQL (mesma versão do RDS) e SQS local
 * (ElasticMQ) — o envio de notificações passa pelo outbox e pela fila como na AWS.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int SQS_PORT = 9324;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    GenericContainer<?> sqsContainer() {
        return new GenericContainer<>(DockerImageName.parse("softwaremill/elasticmq-native:1.7.1"))
                .withExposedPorts(SQS_PORT);
    }

    @Bean
    DynamicPropertyRegistrar sqsProperties(GenericContainer<?> sqsContainer) {
        return registry -> {
            registry.add("spring.cloud.aws.sqs.endpoint",
                    () -> "http://" + sqsContainer.getHost() + ":" + sqsContainer.getMappedPort(SQS_PORT));
            registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
            registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        };
    }
}
