package com.manacommunity.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.manacommunity.api.booking",
        "com.manacommunity.api.user.service",
        "com.manacommunity.common.security",
        "com.manacommunity.common.user.service"
})
@EntityScan(basePackages = {
        "com.manacommunity.api.booking.entity",
        "com.manacommunity.common.model",
        "com.manacommunity.common.user.model"
})
@EnableJpaRepositories(basePackages = {
        "com.manacommunity.api.booking.repository",
        "com.manacommunity.common.repository",
        "com.manacommunity.common.user.repository"
})
public class ResourceBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceBookingApplication.class, args);
    }
}
