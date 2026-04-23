package com.imyme.mine.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정
 * - 메인 서버가 Consume하는 Response 큐는 직접 선언 (Self-healing)
 * - Request 큐는 AI 서버에서 생성
 * - Jackson2JsonMessageConverter: JSON 직렬화/역직렬화
 * - Manual Ack: 메시지 처리 확인 후 수동 승인 (신뢰성 보장)
 */
@Configuration
public class RabbitMQConfig {

    // ===== 큐/라우팅 키 상수 =====

    // DLX / DLQ
    public static final String PVP_DLX = "be.pvp.dlx";
    public static final String PVP_DLQ = "be.pvp.dlq";
    public static final String SOLO_DLX = "be.solo.dlx";
    public static final String SOLO_DLQ = "be.solo.dlq";
    public static final String CHALLENGE_DLX = "be.challenge.dlx";
    public static final String CHALLENGE_DLQ = "be.challenge.dlq";

    public static final String PVP_DIRECT_EXCHANGE = "pvp.direct";

    // Phase 1: STT (Speech-to-Text)
    public static final String PVP_STT_REQUEST_QUEUE = "pvp.stt.request";
    public static final String PVP_STT_RESPONSE_QUEUE = "pvp.stt.response";
    public static final String PVP_STT_REQUEST_ROUTING_KEY = "pvp.stt.request";
    public static final String PVP_STT_RESPONSE_ROUTING_KEY = "pvp.stt.response";

    // Phase 2: Feedback
    public static final String PVP_FEEDBACK_REQUEST_QUEUE = "pvp.feedback.request";
    public static final String PVP_FEEDBACK_RESPONSE_QUEUE = "pvp.feedback.response";
    public static final String PVP_FEEDBACK_REQUEST_ROUTING_KEY = "pvp.feedback.request";
    public static final String PVP_FEEDBACK_RESPONSE_ROUTING_KEY = "pvp.feedback.response";

    // ===== PvP DLX / DLQ =====

    @Bean
    public FanoutExchange pvpDlx() {
        return new FanoutExchange(PVP_DLX, true, false);
    }

    @Bean
    public Queue pvpDlq() {
        return QueueBuilder.durable(PVP_DLQ).build();
    }

    @Bean
    public Binding pvpDlqBinding(Queue pvpDlq, FanoutExchange pvpDlx) {
        return BindingBuilder.bind(pvpDlq).to(pvpDlx);
    }

    // ===== Solo DLX / DLQ =====

    @Bean
    public FanoutExchange soloDlx() {
        return new FanoutExchange(SOLO_DLX, true, false);
    }

    @Bean
    public Queue soloDlq() {
        return QueueBuilder.durable(SOLO_DLQ).build();
    }

    @Bean
    public Binding soloDlqBinding(Queue soloDlq, FanoutExchange soloDlx) {
        return BindingBuilder.bind(soloDlq).to(soloDlx);
    }

    // ===== Exchange / Queue / Binding (메인 서버가 Consume하는 Response 큐만 선언) =====

    @Bean
    public DirectExchange pvpDirectExchange() {
        return new DirectExchange(PVP_DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public Queue pvpSttResponseQueue() {
        return QueueBuilder.durable(PVP_STT_RESPONSE_QUEUE).build();
    }

    @Bean
    public Queue pvpFeedbackResponseQueue() {
        return QueueBuilder.durable(PVP_FEEDBACK_RESPONSE_QUEUE).build();
    }

    @Bean
    public Binding pvpSttResponseBinding(Queue pvpSttResponseQueue, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpSttResponseQueue).to(pvpDirectExchange).with(PVP_STT_RESPONSE_ROUTING_KEY);
    }

    @Bean
    public Binding pvpFeedbackResponseBinding(Queue pvpFeedbackResponseQueue, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpFeedbackResponseQueue).to(pvpDirectExchange).with(PVP_FEEDBACK_RESPONSE_ROUTING_KEY);
    }

    // ===== Challenge DLX / DLQ =====

    @Bean
    public FanoutExchange challengeDlx() {
        return new FanoutExchange(CHALLENGE_DLX, true, false);
    }

    @Bean
    public Queue challengeDlq() {
        return QueueBuilder.durable(CHALLENGE_DLQ).build();
    }

    @Bean
    public Binding challengeDlqBinding(Queue challengeDlq, FanoutExchange challengeDlx) {
        return BindingBuilder.bind(challengeDlq).to(challengeDlx);
    }

    // ===== Challenge Exchange / Queue / Binding (Response 큐만 선언) =====

    @Bean
    public DirectExchange challengeDirectExchange(ChallengeMqProperties props) {
        return new DirectExchange(props.getExchange(), true, false);
    }

    @Bean
    public Queue challengeFeedbackResponseQueue(ChallengeMqProperties props) {
        return QueueBuilder.durable(props.getQueue().getFeedbackResponse()).build();
    }

    @Bean
    public Binding challengeFeedbackResponseBinding(
            Queue challengeFeedbackResponseQueue,
            DirectExchange challengeDirectExchange,
            ChallengeMqProperties props) {
        return BindingBuilder.bind(challengeFeedbackResponseQueue)
                .to(challengeDirectExchange)
                .with(props.getQueue().getFeedbackResponse());
    }

    // ===== Challenge 토너먼트 큐 =====

    /** 토너먼트 최종 완료 큐 — AI → Spring (BE가 소비하는 큐만 선언) */
    @Bean
    public Queue challengeFinalDoneQueue(ChallengeMqProperties props) {
        return QueueBuilder.durable(props.getQueue().getFinalDone()).build();
    }

    @Bean
    public Binding challengeFinalDoneBinding(
            Queue challengeFinalDoneQueue,
            DirectExchange challengeDirectExchange,
            ChallengeMqProperties props) {
        return BindingBuilder.bind(challengeFinalDoneQueue)
                .to(challengeDirectExchange)
                .with(props.getQueue().getFinalDone());
    }

    // ===== Solo Exchange / Queue / Binding (Response 큐만 선언) =====

    @Bean
    public DirectExchange soloDirectExchange(SoloMqProperties soloMqProperties) {
        return new DirectExchange(soloMqProperties.getExchange(), true, false);
    }

    @Bean
    public Queue soloSttResponseQueue(SoloMqProperties soloMqProperties) {
        return QueueBuilder.durable(soloMqProperties.getQueue().getSttResponse()).build();
    }

    @Bean
    public Queue soloFeedbackResponseQueue(SoloMqProperties soloMqProperties) {
        return QueueBuilder.durable(soloMqProperties.getQueue().getFeedbackResponse()).build();
    }

    @Bean
    public Binding soloSttResponseBinding(Queue soloSttResponseQueue, DirectExchange soloDirectExchange,
                                           SoloMqProperties soloMqProperties) {
        return BindingBuilder.bind(soloSttResponseQueue).to(soloDirectExchange)
            .with(soloMqProperties.getQueue().getSttResponse());
    }

    @Bean
    public Binding soloFeedbackResponseBinding(Queue soloFeedbackResponseQueue, DirectExchange soloDirectExchange,
                                                SoloMqProperties soloMqProperties) {
        return BindingBuilder.bind(soloFeedbackResponseQueue).to(soloDirectExchange)
            .with(soloMqProperties.getQueue().getFeedbackResponse());
    }

    // ===== Message Converter =====

    /**
     * Jackson2JsonMessageConverter: JSON 직렬화/역직렬화
     * - 모든 메시지는 JSON 형태로 송수신
     * - LocalDateTime 등 Java 8 시간 타입 지원
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate: 메시지 발행 (Producer)
     * - Jackson2JsonMessageConverter 적용
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    /**
     * SimpleRabbitListenerContainerFactory: 메시지 수신 (Consumer)
     * - Jackson2JsonMessageConverter 적용
     * - Manual Ack 모드 (application.yml에서 설정)
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);   // application.yml 설정 적용
        factory.setMessageConverter(messageConverter);       // 커스텀 converter 유지
        return factory;
    }
}
