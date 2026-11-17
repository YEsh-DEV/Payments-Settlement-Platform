package io.github.yesh.paycore.settlement.config;

import io.github.yesh.paycore.common.web.RequestIdFilter;
import io.github.yesh.paycore.common.web.SecurityHeadersFilter;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
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

  @Value("${paycore.security.enabled:true}")
  private boolean securityEnabled;

  @Value("${paycore.security.jwks-uri:http://localhost:8081/.well-known/jwks.json}")
  private String jwksUri;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(securityHeadersFilter, RequestIdFilter.class)
        .addFilterAfter(requestBodyLimitFilter, SecurityHeadersFilter.class);

    if (!securityEnabled) {
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
      return http.build();
    }

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/error")
                    .permitAll()
                    // Playbook Section 12: Batches
                    .requestMatchers(HttpMethod.POST, "/v1/batches/run", "/v1/batches/cutoff")
                    .hasAnyRole("ADMIN", "SERVICE")
                    .requestMatchers(HttpMethod.GET, "/v1/batches/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    // Payouts
                    .requestMatchers(HttpMethod.GET, "/v1/payouts/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    // Provider Files & Bank Statements
                    .requestMatchers(
                        HttpMethod.POST,
                        "/v1/provider-files/**",
                        "/v1/bank-statements/**",
                        "/v1/files/**")
                    .hasAnyRole("ANALYST", "ADMIN", "SERVICE")
                    .requestMatchers(HttpMethod.GET, "/v1/files/**")
                    .hasAnyRole("ANALYST", "ADMIN", "SERVICE")
                    // Reconciliation
                    .requestMatchers("/v1/recon/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    // Cases
                    .requestMatchers("/v1/cases/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    // Adjustments (Approve/Reject: ADMIN only; Read: ANALYST, ADMIN)
                    .requestMatchers(
                        HttpMethod.POST, "/v1/adjustments/*/approve", "/v1/adjustments/*/reject")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/v1/adjustments/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    // Dashboard
                    .requestMatchers("/v1/dashboard/**")
                    .hasAnyRole("ANALYST", "ADMIN")
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
