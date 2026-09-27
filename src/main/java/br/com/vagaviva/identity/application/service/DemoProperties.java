package br.com.vagaviva.identity.application.service;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code vagaviva.demo}: ligado nos perfis local e demo/hml; senha dos usuários vinda de {@code DEMO_USERS_PASSWORD}. */
@ConfigurationProperties("vagaviva.demo")
public record DemoProperties(boolean enabled, @Nullable String usersPassword) {
}
