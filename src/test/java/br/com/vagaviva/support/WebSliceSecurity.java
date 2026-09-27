package br.com.vagaviva.support;

import br.com.vagaviva.shared.config.JwtKeyConfig;
import br.com.vagaviva.shared.config.SecurityConfig;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Segurança real (JWT, papéis, 401/403 em problem+json) nos testes {@code @WebMvcTest}. */
@TestConfiguration
@Import({SecurityConfig.class, JwtKeyConfig.class, CurrentUserProvider.class})
public class WebSliceSecurity {
}
