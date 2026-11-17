package io.github.yesh.paycore.identity.security;

import io.github.yesh.paycore.common.web.RedisRateLimiterFilter;
import io.github.yesh.paycore.common.web.RequestIdFilter;
import io.github.yesh.paycore.common.web.SecurityHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final StringRedisTemplate redisTemplate;
  private final RequestIdFilter requestIdFilter = new RequestIdFilter();
  private final SecurityHeadersFilter securityHeadersFilter = new SecurityHeadersFilter();
  private final io.github.yesh.paycore.common.web.RequestBodyLimitFilter requestBodyLimitFilter =
      new io.github.yesh.paycore.common.web.RequestBodyLimitFilter();

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.redisTemplate = redisTemplateProvider.getIfAvailable();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                        org.springframework.http.HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/service-token",
                        "/auth/logout",
                        "/.well-known/jwks.json",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(securityHeadersFilter, RequestIdFilter.class)
        .addFilterAfter(requestBodyLimitFilter, SecurityHeadersFilter.class);

    if (redisTemplate != null) {
      http.addFilterBefore(
          new RedisRateLimiterFilter(redisTemplate), UsernamePasswordAuthenticationFilter.class);
    }

    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
