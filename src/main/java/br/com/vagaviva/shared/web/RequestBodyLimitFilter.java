package br.com.vagaviva.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limite do corpo das requisições (OWASP API4 — consumo de recursos): {@code Content-Length} acima do
 * limite é recusado com 413 antes de qualquer processamento; corpo sem tamanho declarado (chunked) é
 * contado na leitura. O maior corpo legítimo da API é o lote de 200 vagas (~20 KB).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final HandlerExceptionResolver exceptionResolver;

    public RequestBodyLimitFilter(@Value("${vagaviva.http.max-request-body:256KB}") DataSize maxBody,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.maxBytes = maxBody.toBytes();
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            exceptionResolver.resolveException(request, response, null, new PayloadTooLargeException(maxBytes));
            return;
        }
        chain.doFilter(request.getContentLengthLong() < 0 ? new LimitedRequest(request, maxBytes) : request, response);
    }

    /** Conta os bytes lidos de um corpo sem tamanho declarado. */
    static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    if (value >= 0) {
                        count(1);
                    }
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int n = delegate.read(buffer, offset, length);
                    if (n > 0) {
                        count(n);
                    }
                    return n;
                }

                private void count(int n) {
                    read += n;
                    if (read > maxBytes) {
                        throw new PayloadTooLargeException(maxBytes);
                    }
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
