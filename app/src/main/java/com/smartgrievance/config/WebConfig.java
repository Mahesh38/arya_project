package com.smartgrievance.config;

import java.util.Locale;

import com.smartgrievance.otp.VerifiedCitizenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final VerifiedCitizenInterceptor verifiedCitizen;

    public WebConfig(VerifiedCitizenInterceptor verifiedCitizen) {
        this.verifiedCitizen = verifiedCitizen;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(verifiedCitizen).addPathPatterns("/citizen/complaints/new", "/citizen/complaints");
    }

    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
}
