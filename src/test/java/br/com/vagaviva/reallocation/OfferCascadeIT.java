package br.com.vagaviva.reallocation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.reallocation.application.port.in.ExpireOffersUseCase;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-31: rodadas em cascata até alguém aceitar; esgotou ⇒ vaga perdida (RN-15). */
@IntegrationTest
@Import({SchedulingTestData.class, ReallocationTestData.class})
class OfferCascadeIT {

    @Autowired ReallocationTestData data;
    @Autowired ReallocationApi reallocation;
    @Autowired ExpireOffersUseCase expirations;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("rodada 1 (3 mais antigos) recusada por todos ⇒ rodada 2 para o 4º, que aceita")
    void cascadesUntilAccepted() {
        UUID specialty = data.specialty();
        List<Referral> first = List.of(data.shortNoticeReferral(specialty, 40), data.shortNoticeReferral(specialty, 30),
                data.shortNoticeReferral(specialty, 20));
        Referral fourth = data.shortNoticeReferral(specialty, 10);
        UUID slot = data.publishSlot(specialty, 30);

        data.awaitPendingOffers(slot, 1, 3);
        assertThat(jdbc.queryForList("select referral_id from slot_offer where slot_id = ? and round = 1", UUID.class,
                slot)).containsExactlyInAnyOrderElementsOf(first.stream().map(Referral::id).toList());
        first.forEach(referral -> assertThat(reallocation.decline(data.offerOf(referral.id())).status())
                .isEqualTo(OfferStatus.DECLINED));

        UUID secondRound = data.awaitPendingOffers(slot, 2, 1).getFirst();
        assertThat(jdbc.queryForObject("select referral_id from slot_offer where id = ?", UUID.class, secondRound))
                .isEqualTo(fourth.id());
        var accepted = reallocation.accept(secondRound);

        assertThat(data.status("appointment", accepted.appointmentId())).isEqualTo("CONFIRMED");
        assertThat(data.status("referral", fourth.id())).isEqualTo("SCHEDULED");
        assertThat(first.stream().map(r -> data.status("referral", r.id())).toList()).containsOnly("WAITING");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'OFFER_ROUND_STARTED' "
                + "and resource_id = ?", Long.class, slot.toString())).isEqualTo(2);
    }

    @Test
    @DisplayName("oferta sem resposta expira pelo job; sem mais ninguém que aceite encaixe ⇒ vaga EXPIRED (perdida)")
    void exhaustionLosesSlot() {
        UUID specialty = data.specialty();
        Referral only = data.shortNoticeReferral(specialty, 15);
        UUID slot = data.publishSlot(specialty, 30);
        UUID offer = data.awaitPendingOffers(slot, 1, 1).getFirst();

        jdbc.update("update slot_offer set expires_at = now() - interval '1 minute' where id = ?", offer);
        expirations.expireOverdue();

        assertThat(data.status("slot_offer", offer)).isEqualTo("EXPIRED");
        assertThat(data.status("slot", slot)).isEqualTo("EXPIRED");
        assertThat(data.status("referral", only.id())).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'SLOT_LOST' and resource_id = ?",
                Long.class, slot.toString())).isEqualTo(1);

        UUID nextSlot = data.publishSlot(specialty, 40);
        UUID nextOffer = data.awaitPendingOffers(nextSlot, 1, 1).getFirst();
        assertThat(data.awaitOfferToken(nextOffer)).as("segunda oferta ao mesmo paciente também é enviada").hasSize(22);
    }
}
