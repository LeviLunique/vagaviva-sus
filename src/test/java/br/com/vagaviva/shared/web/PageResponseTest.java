package br.com.vagaviva.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    @DisplayName("converte a página do Spring Data no envelope da API, mapeando cada item")
    void shouldMapSpringPage() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.of(page, n -> "item-" + n);

        assertThat(response.content()).containsExactly("item-1", "item-2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
