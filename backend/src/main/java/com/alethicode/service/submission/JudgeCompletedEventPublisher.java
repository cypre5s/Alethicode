package com.alethicode.service.submission;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class JudgeCompletedEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public JudgeCompletedEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(JudgeCompletedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
