package com.smartgrievance.attachment;

import com.smartgrievance.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
class UploadFilterConfig {
    @Bean
    FilterRegistrationBean<UploadSizeGuardFilter> uploadSizeGuard(
            @Value("${spring.servlet.multipart.max-request-size}") String limit, AppProperties properties,
            MessageSource messages) {
        var filter = new UploadSizeGuardFilter(DataSize.parse(limit).toBytes(), properties.upload(), messages);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-110);
        return registration;
    }
}
