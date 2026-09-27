package br.com.vagaviva.reallocation.adapter.in.web;

import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.UNIT;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.pendingOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase;
import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase.OfferFilter;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SlotOfferController.class)
@Import(WebSliceSecurity.class)
class SlotOfferControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean SlotOfferQueryUseCase queries;

    @Test
    @DisplayName("RF-33: rodadas e status das ofertas de uma vaga (SCHEDULER da unidade, REGULATOR, ADMIN)")
    void shouldListOffers() {
        UUID slot = UUID.randomUUID();
        SlotOffer offer = pendingOffer(slot, 1);
        when(queries.list(eq(new OfferFilter(slot, OfferStatus.PENDING)), any(), any()))
                .thenReturn(new PageImpl<>(List.of(offer)));
        when(queries.list(eq(new OfferFilter(null, null)), any(), any()))
                .thenThrow(new ForbiddenOperationException("OFFERS_OUT_OF_UNIT", "Informe uma vaga da sua unidade."));

        var page = mvc.get().uri("/api/v1/slot-offers?slotId={s}&status=PENDING", slot)
                .with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UNIT)).exchange();
        assertThat(page).hasStatusOk();
        assertThat(page).bodyJson().extractingPath("$.content[0].round").isEqualTo(1);
        assertThat(page).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING");
        assertThat(mvc.get().uri("/api/v1/slot-offers").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UNIT)))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("OFFERS_OUT_OF_UNIT");
        assertThat(mvc.get().uri("/api/v1/slot-offers").with(TestJwt.as(Role.REQUESTER))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/slot-offers")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/slot-offers?status=NOPE").with(TestJwt.as(Role.ADMIN)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
