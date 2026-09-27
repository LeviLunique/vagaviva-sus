package br.com.vagaviva.engagement.adapter.out.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.socialmessaging.SocialMessagingClient;

/** Cliente do AWS End User Messaging Social — criado só com o WhatsApp habilitado. */
@Configuration
@ConditionalOnBooleanProperty("vagaviva.engagement.whatsapp.enabled")
class WhatsAppClientConfig {

    @Bean
    SocialMessagingClient socialMessagingClient(@Value("${spring.cloud.aws.region.static}") String region) {
        return SocialMessagingClient.builder().region(Region.of(region)).build();
    }
}
