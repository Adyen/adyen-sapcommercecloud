package com.adyen.commerce.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class AdyenSecurityConfig {

    @Bean
    public SecurityFilterChain adyenSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/adyen/v6/notification/**"),
                                new AntPathRequestMatcher("/api/checkout/**") // Often needed for headless checkout endpoints
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/adyen/v6/notification/**")).permitAll()
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}