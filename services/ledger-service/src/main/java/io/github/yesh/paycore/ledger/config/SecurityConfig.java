package io.github.yesh.paycore.ledger.config;

import io.github.yesh.paycore.common.web.RedisRateLimiterFilter;
import io.github.yesh.paycore.common.web.RequestIdFilter;
import io.github.yesh.paycore.common.web.SecurityHeadersFilter;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final RequestIdFilter requestIdFilter = new RequestIdFilter();
  private final SecurityHeadersFilter securityHeadersFilter = new SecurityHeadersFilter();
  private final io.github.yesh.paycore.common.web.RequestBodyLimitFilter requestBodyLimitFilter =
      new io.github.yesh.paycore.common.web.RequestBodyLimitFilter();
  private final StringRedisTemplate redisTemplate;

  @Value("${paycore.security.enabled:true}")
  private boolean securityEnabled;

  @Value("${paycore.security.jwks-uri:http://localhost:8081/.well-known/jwks.json}")
  private String jwksUri;

  public SecurityConfig(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplate = redisTemplateProvider.getIfAvailable();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(securityHeadersFilter, RequestIdFilter.class)
        .addFilterAfter(requestBodyLimitFilter, SecurityHeadersFilter.class);

    if (redisTemplate != null) {
      http.addFilterBefore(
          new RedisRateLimiterFilter(redisTemplate), UsernamePasswordAuthenticationFilter.class);
    }

    if (!securityEnabled) {
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
      return http.build();
    }

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/error")
                    .permitAll()
                    // Playbook Section 12: only SERVICE may post payouts, deposits and adjustments
                    .requestMatchers(HttpMethod.POST, "/v1/payouts/**", "/payouts/**")
                    .hasRole("SERVICE")
                    .requestMatchers(HttpMethod.POST, "/v1/provider-deposits", "/provider-deposits")
                    .hasRole("SERVICE")
                    .requestMatchers(HttpMethod.POST, "/v1/adjustments", "/adjustments")
                    .hasRole("SERVICE")
                    // Payments & Refunds & Sellers
                    .requestMatchers(HttpMethod.POST, "/v1/payments", "/payments")
                    .hasAnyRole("SERVICE", "ADMIN")
                    .requestMatchers(
                        HttpMethod.POST, "/v1/payments/*/refunds", "/payments/*/refunds")
                    .hasAnyRole("SERVICE", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/v1/sellers", "/sellers")
                    .hasAnyRole("SERVICE", "ADMIN")
                    // Analyst and Admin endpoints
                    .requestMatchers(
                        HttpMethod.POST,
                        "/v1/holds",
                        "/holds",
                        "/v1/holds/*/release",
                        "/holds/*/release")
                    .hasAnyRole("ANALYST", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/v1/integrity/**", "/integrity/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/v1/integrity/**", "/integrity/**")
                    .hasAnyRole("ADMIN")
                    // Read endpoints
                    .requestMatchers(HttpMethod.GET, "/v1/sellers/**", "/sellers/**")
                    .hasAnyRole("MERCHANT", "ANALYST", "ADMIN", "SERVICE")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/v1/accounts/**",
                        "/accounts/**",
                        "/v1/journals/**",
                        "/journals/**")
                    .hasAnyRole("MERCHANT", "ANALYST", "ADMIN", "SERVICE")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        new Converter<Jwt, Collection<GrantedAuthority>>() {
          @Override
          public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
              String single = jwt.getClaimAsString("role");
              if (single != null) {
                roles = List.of(single);
              } else {
                roles = List.of();
              }
            }
            return roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
          }
        });
    return converter;
  }

  @Bean
  @ConditionalOnProperty(
      name = "paycore.security.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
  }
}
