package br.com.vagaviva.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Envelope de paginação da API (SPEC §4.1): {@code page} começa em 0. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().<T>map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
