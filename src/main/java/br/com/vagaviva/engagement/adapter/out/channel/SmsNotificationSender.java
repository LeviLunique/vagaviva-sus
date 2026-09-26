package br.com.vagaviva.engagement.adapter.out.channel;

import br.com.vagaviva.engagement.application.port.out.NotificationSender;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** SMS pelo Amazon SNS, tipo transacional (entrega priorizada). Habilitado por {@code sms.enabled}. */
@Component
@ConditionalOnBooleanProperty("vagaviva.engagement.sms.enabled")
class SmsNotificationSender implements NotificationSender {

    static final String SMS_TYPE = "AWS.SNS.SMS.SMSType";

    private final SnsClient sns;

    SmsNotificationSender(SnsClient sns) {
        this.sns = sns;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public String send(String phoneE164, String body) {
        return sns.publish(PublishRequest.builder()
                .phoneNumber(phoneE164)
                .message(body)
                .messageAttributes(Map.of(SMS_TYPE,
                        MessageAttributeValue.builder().dataType("String").stringValue("Transactional").build()))
                .build()).messageId();
    }
}
