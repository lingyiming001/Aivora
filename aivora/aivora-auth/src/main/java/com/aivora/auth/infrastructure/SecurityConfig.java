package com.aivora.auth.infrastructure;

import com.aivora.auth.service.AuthService;
import com.aivora.auth.service.GuestService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain chain(
    org.springframework.security.config.annotation.web.builders.HttpSecurity http,
    AuthService auth,
    GuestService guests,
    @Value("${aivora.cors-origin}") String origin
  ) throws Exception {
    CorsConfiguration cors = new CorsConfiguration();
    var origins = new java.util.ArrayList<String>();
    origins.add(origin);
    java.net.URI originUri = java.net.URI.create(origin);
    if ("localhost".equals(originUri.getHost())) origins.add(
      origin.replace("://localhost", "://127.0.0.1")
    );
    if ("127.0.0.1".equals(originUri.getHost())) origins.add(
      origin.replace("://127.0.0.1", "://localhost")
    );
    cors.setAllowedOrigins(origins);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
      List.of("Authorization", "Content-Type", "Idempotency-Key", "MCP-Protocol-Version")
    );
    cors.setExposedHeaders(List.of("Content-Type"));
    http
      .csrf(x -> x.disable())
      .cors(x -> x.configurationSource(req -> cors))
      .sessionManagement(x ->
        x.sessionCreationPolicy(
          org.springframework.security.config.http.SessionCreationPolicy.STATELESS
        )
      )
      .authorizeHttpRequests(x ->
        x
          .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
          .permitAll()
          .requestMatchers(
            "/api/auth/**",
            "/api/live-overlay/*",
            "/api/live-media/publish",
            "/api/live-view/*/*",
            "/api/guest/session",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**"
          )
          .permitAll()
          .requestMatchers("/api/characters/mine")
          .authenticated()
          .requestMatchers(HttpMethod.GET, "/api/characters", "/api/characters/*", "/api/models")
          .permitAll()
          .requestMatchers("/api/admin/**")
          .hasRole("ADMIN")
          .requestMatchers("/api/chats/**")
          .hasAnyRole("USER", "ADMIN", "GUEST")
          .anyRequest()
          .hasAnyRole("USER", "ADMIN")
      )
      .addFilterBefore(new OncePerRequestFilter(auth, guests), BasicAuthenticationFilter.class);
    return http.build();
  }

  static class OncePerRequestFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private final AuthService auth;
    private final GuestService guests;

    OncePerRequestFilter(AuthService auth, GuestService guests) {
      this.auth = auth;
      this.guests = guests;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
      return false;
    }

    @Override
    protected void doFilterInternal(
      HttpServletRequest req,
      HttpServletResponse res,
      FilterChain chain
    ) throws ServletException, IOException {
      String header = req.getHeader("Authorization");
      if (header != null && header.startsWith("Bearer ")) {
        try {
          Long id = auth.verify(header.substring(7));
          SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
              id,
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + auth.verifiedRole(header.substring(7))))
            )
          );
        } catch (RuntimeException ex) {
          SecurityContextHolder.clearContext();
        }
      }
      if (
        SecurityContextHolder.getContext().getAuthentication() == null &&
        (req.getRequestURI().startsWith("/api/chats") ||
          req.getRequestURI().equals("/api/guest/session"))
      ) {
        Long guest = guests.identify(req);
        if (guest != null) SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(
            guest,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
          )
        );
      }
      chain.doFilter(req, res);
    }
  }
}
