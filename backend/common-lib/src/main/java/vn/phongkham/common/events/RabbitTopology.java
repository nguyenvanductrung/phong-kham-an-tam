package vn.phongkham.common.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@ConditionalOnProperty(name="events.enabled",havingValue="true")
public class RabbitTopology {
    @Bean TopicExchange clinicEventsExchange(){return new TopicExchange("clinic.events",true,false);}
    @Bean TopicExchange clinicDeadLetterExchange(){return new TopicExchange("clinic.events.dlx",true,false);}
    @Bean Queue notificationQueue(){return new Queue("notification.events",true,false,false,java.util.Map.of("x-dead-letter-exchange","clinic.events.dlx","x-dead-letter-routing-key","notification.failed"));}
    @Bean Queue patientQueue(){return new Queue("patient.events",true,false,false,java.util.Map.of("x-dead-letter-exchange","clinic.events.dlx","x-dead-letter-routing-key","patient.failed"));}
    @Bean Queue schedulingQueue(){return new Queue("scheduling.events",true,false,false,java.util.Map.of("x-dead-letter-exchange","clinic.events.dlx","x-dead-letter-routing-key","scheduling.failed"));}
    @Bean Queue notificationDlq(){return new Queue("notification.events.dlq",true);}
    @Bean Queue patientDlq(){return new Queue("patient.events.dlq",true);}
    @Bean Queue schedulingDlq(){return new Queue("scheduling.events.dlq",true);}
    @Bean Queue outboxDlq(){return new Queue("outbox.events.dlq",true);}
    @Bean Binding bindNotification(Queue notificationQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(notificationQueue).to(clinicEventsExchange).with("#");}
    @Bean Binding bindPatientBooked(Queue patientQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(patientQueue).to(clinicEventsExchange).with("AppointmentBooked.v1");}
    @Bean Binding bindPatientRescheduled(Queue patientQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(patientQueue).to(clinicEventsExchange).with("AppointmentRescheduled.v1");}
    @Bean Binding bindPatientCancelled(Queue patientQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(patientQueue).to(clinicEventsExchange).with("AppointmentCancelled.v1");}
    @Bean Binding bindPatientCheckedIn(Queue patientQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(patientQueue).to(clinicEventsExchange).with("PatientCheckedIn.v1");}
    @Bean Binding bindPatientNoShow(Queue patientQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(patientQueue).to(clinicEventsExchange).with("AppointmentNoShow.v1");}
    @Bean Binding bindScheduling(Queue schedulingQueue,TopicExchange clinicEventsExchange){return BindingBuilder.bind(schedulingQueue).to(clinicEventsExchange).with("ClinicalRecordSigned.v1");}
    @Bean Binding bindNotificationDlq(Queue notificationDlq,TopicExchange clinicDeadLetterExchange){return BindingBuilder.bind(notificationDlq).to(clinicDeadLetterExchange).with("notification.failed");}
    @Bean Binding bindPatientDlq(Queue patientDlq,TopicExchange clinicDeadLetterExchange){return BindingBuilder.bind(patientDlq).to(clinicDeadLetterExchange).with("patient.failed");}
    @Bean Binding bindSchedulingDlq(Queue schedulingDlq,TopicExchange clinicDeadLetterExchange){return BindingBuilder.bind(schedulingDlq).to(clinicDeadLetterExchange).with("scheduling.failed");}
    @Bean Binding bindOutboxDlq(Queue outboxDlq,TopicExchange clinicDeadLetterExchange){return BindingBuilder.bind(outboxDlq).to(clinicDeadLetterExchange).with("outbox.failed");}
}

