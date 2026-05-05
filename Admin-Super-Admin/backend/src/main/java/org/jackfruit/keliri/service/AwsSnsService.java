package org.jackfruit.keliri.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;

@Service
public class AwsSnsService {

    @Autowired
    private SnsClient snsClient;

    @Value("${aws.sns.smsType:Transactional}")
    private String smsType;

    @Value("${aws.sns.senderId:}")
    private String senderId;

    @Value("${aws.sns.entityId:}")
    private String entityId;

    @Value("${aws.sns.templateId:}")
    private String templateId;

    public boolean sendSms(String phoneNumber, String message) {
        try {
            Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();
            smsAttributes.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                    .stringValue(senderId.isEmpty() ? "KELIRI" : senderId)
                    .dataType("String")
                    .build());
            smsAttributes.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                    .stringValue(smsType)
                    .dataType("String")
                    .build());

            // Add DLT attributes for India if provided
            if (!entityId.isEmpty()) {
                smsAttributes.put("AWS.MM.SMS.EntityId", MessageAttributeValue.builder()
                        .stringValue(entityId)
                        .dataType("String")
                        .build());
            }
            if (!templateId.isEmpty()) {
                smsAttributes.put("AWS.MM.SMS.TemplateId", MessageAttributeValue.builder()
                        .stringValue(templateId)
                        .dataType("String")
                        .build());
            }

            PublishRequest request = PublishRequest.builder()
                    .message(message)
                    .phoneNumber(phoneNumber)
                    .messageAttributes(smsAttributes)
                    .build();

            PublishResponse result = snsClient.publish(request);
            System.out.println("SMS sent successfully to " + phoneNumber + ". Message ID: " + result.messageId());
            return true;
        } catch (Exception e) {
            System.err.println("Error sending SMS via AWS SNS: " + e.getMessage());
            return false;
        }
    }
}
