package com.deskdibs.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application's source of truth for the current instant.
 *
 * <p>Exposed as a bean purely so it can be replaced. Production runs the system clock; a test
 * substitutes a movable one and can then step over the 10:00 team-block release without sleeping.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public Clock systemClock(OfficeProperties office) {
        return Clock.system(office.timezone());
    }
}
