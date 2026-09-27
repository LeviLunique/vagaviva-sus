package br.com.vagaviva.reallocation.adapter.in.web;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase;
import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase.OfferFilter;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Encaixe", description = "Ofertas de encaixe em cascata das vagas liberadas (RF-31 a RF-33)")
@RestController
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel ou unidade sem permissão", content = @Content(mediaType = "application/problem+json"))
class SlotOfferController {

    private final SlotOfferQueryUseCase queries;
    private final CurrentUserProvider currentUser;

    SlotOfferController(SlotOfferQueryUseCase queries, CurrentUserProvider currentUser) {
        this.queries = queries;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Ofertas de encaixe (ADMIN, SCHEDULER, REGULATOR)",
            description = "Rodadas, status e respostas. SCHEDULER informa uma vaga da própria unidade.")
    @ApiResponse(responseCode = "200", description = "Página de ofertas, por rodada")
    @GetMapping("/api/v1/slot-offers")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER','REGULATOR')")
    PageResponse<OfferResponse> list(@RequestParam(required = false) UUID slotId,
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(queries.list(new OfferFilter(slotId, status), PageRequest.of(page, size),
                currentUser.get()), OfferResponse::from);
    }

    record OfferResponse(UUID id, UUID slotId, UUID referralId, UUID patientId, int round, OfferStatus status,
            Instant expiresAt, Instant respondedAt, Instant createdAt) {

        static OfferResponse from(SlotOffer o) {
            return new OfferResponse(o.id(), o.slotId(), o.referralId(), o.patientId(), o.round(), o.status(),
                    o.expiresAt(), o.respondedAt(), o.createdAt());
        }
    }
}
