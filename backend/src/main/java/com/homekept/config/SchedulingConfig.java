package com.homekept.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support for background jobs — first consumer is
 * {@code com.homekept.notification.ReminderScheduler} (#89).
 *
 * <p>Per arch doc §5.3 MVP guidance: plain {@code @Scheduled}, no durable job queue yet
 * (Post-MVP: Jobrunr adds durable storage, retry, and a dashboard).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
