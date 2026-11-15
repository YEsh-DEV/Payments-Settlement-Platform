package io.github.yesh.paycore.identity.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final RsaKeyProvider rsaKeyProvider;
  private final TokenDenyListService tokenDenyListService;

  public JwtAuthenticationFilter(
      RsaKeyProvider rsaKeyProvider, TokenDenyListService tokenDenyListService) {
    this.rsaKeyProvider = rsaKeyProvider;
    this.tokenDenyListService = tokenDenyListService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      try {
        SignedJWT signedJwt = SignedJWT.parse(token);
        RSAPublicKey publicKey =
            (RSAPublicKey)
                rsaKeyProvider.getPublicJwkSet().getKeys().get(0).toRSAKey().toPublicKey();
        JWSVerifier verifier = new RSASSAVerifier(publicKey);

        if (signedJwt.verify(verifier)) {
          JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
          Date expirationTime = claims.getExpirationTime();

          if (expirationTime != null && expirationTime.after(new Date())) {
            String jti = claims.getJWTID();
            if (jti != null && !tokenDenyListService.isRevoked(jti)) {
              List<String> roles = claims.getStringListClaim("roles");
              List<SimpleGrantedAuthority> authorities =
                  roles == null
                      ? List.of()
                      : roles.stream()
                          .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                          .map(SimpleGrantedAuthority::new)
                          .collect(Collectors.toList());

              UsernamePasswordAuthenticationToken auth =
                  new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
              SecurityContextHolder.getContext().setAuthentication(auth);
            }
          }
        }
      } catch (Exception ex) {
        // If token invalid, leave context unauthenticated
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }
}
