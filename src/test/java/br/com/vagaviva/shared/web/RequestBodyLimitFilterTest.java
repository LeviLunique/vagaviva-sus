package br.com.vagaviva.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.HandlerExceptionResolver;

class RequestBodyLimitFilterTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(DataSize.ofBytes(10), resolver);

    private static MockHttpServletRequest post(String body, boolean declareLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/slots") {
            @Override
            public long getContentLengthLong() {
                return declareLength ? super.getContentLengthLong() : -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    @DisplayName("Content-Length acima do limite ⇒ 413 pelo resolvedor de exceções, sem chegar ao controller")
    void rejectsDeclaredOversizedBody() throws Exception {
        MockHttpServletRequest request = post("x".repeat(11), true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(resolver).resolveException(eq(request), eq(response), eq(null),
                argThat(ex -> ex instanceof PayloadTooLargeException));
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("corpo dentro do limite passa sem embrulho")
    void passesSmallBody() throws Exception {
        MockHttpServletRequest request = post("x".repeat(10), true);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(eq(request), any());
    }

    @Test
    @DisplayName("sem tamanho declarado (chunked): a leitura é contada e estoura ao passar do limite")
    void countsUndeclaredBody() throws Exception {
        filter.doFilter(post("x".repeat(8), false), new MockHttpServletResponse(), chain);
        filter.doFilter(post("x".repeat(20), false), new MockHttpServletResponse(), chain);

        var wrapped = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain, org.mockito.Mockito.times(2)).doFilter(wrapped.capture(), any());
        var small = wrapped.getAllValues().get(0).getInputStream();
        assertThat(small.readAllBytes()).hasSize(8);
        assertThat(small.read()).isEqualTo(-1);
        assertThat(small.isFinished()).isTrue();
        assertThat(small.isReady()).isTrue();
        assertThatThrownBy(() -> small.setReadListener(null)).as("delegado ao stream do contêiner")
                .isInstanceOf(UnsupportedOperationException.class);
        var big = wrapped.getAllValues().get(1).getInputStream();
        assertThat(big.read()).isEqualTo('x');
        assertThatThrownBy(big::readAllBytes).isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("limite");
    }
}
