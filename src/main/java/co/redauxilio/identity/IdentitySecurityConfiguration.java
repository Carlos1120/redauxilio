package co.redauxilio.identity;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.stereotype.Component;

/** Define acceso público, formularios protegidos contra CSRF y la duración de sesión. */
@Configuration
class IdentitySecurityConfiguration {
  @Bean
  Clock identityClock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  DaoAuthenticationProvider citizenAuthenticationProvider(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      DaoAuthenticationProvider authenticationProvider,
      AuthenticationSuccessHandler loginSuccessHandler,
      AuthenticationFailureHandler loginFailureHandler,
      SessionLifetimeFilter sessionLifetimeFilter,
      @Value("${redauxilio.security.force-https:false}") boolean forceHttps)
      throws Exception {
    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/login",
                        "/register",
                        "/error",
                        "/csrf",
                        "/favicon.ico",
                        "/app.css",
                        "/app.js",
                        "/identity-forms.js",
                        "/identity-status.js",
                        "/publication-form.js",
                        "/map.js",
                        "/sw.js",
                        "/manifest.webmanifest",
                        "/icon.svg",
                        "/vendor/**",
                        "/api/publications/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/login", "/register")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .authenticationProvider(authenticationProvider)
        .exceptionHandling(
            exceptions ->
                exceptions
                    .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new OrRequestMatcher(
                            new AntPathRequestMatcher("/api/places"),
                            new AntPathRequestMatcher("/api/publications", "POST"),
                            new AntPathRequestMatcher("/api/my/**")))
                    .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE))
        .formLogin(
            form ->
                form.loginPage("/login")
                    .successHandler(loginSuccessHandler)
                    .failureHandler(loginFailureHandler)
                    .permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/?access=login&signedout"))
        .sessionManagement(
            session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
        .addFilterAfter(sessionLifetimeFilter, SecurityContextHolderAwareRequestFilter.class);
    if (forceHttps) http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
    return http.build();
  }

  @Bean
  SessionLifetimeFilter sessionLifetimeFilter(Clock clock) {
    return new SessionLifetimeFilter(clock);
  }

  @Bean
  FilterRegistrationBean<SessionLifetimeFilter> disableContainerRegistration(
      SessionLifetimeFilter filter) {
    FilterRegistrationBean<SessionLifetimeFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}

/** Registra el inicio de sesión para aplicar el límite absoluto de edad de sesión. */
@Component
class CitizenLoginSuccessHandler implements AuthenticationSuccessHandler {
  private final CitizenAccountService accounts;
  private final Clock clock;

  CitizenLoginSuccessHandler(CitizenAccountService accounts, Clock clock) {
    this.accounts = accounts;
    this.clock = clock;
  }

  @Override
  public void onAuthenticationSuccess(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      Authentication authentication)
      throws java.io.IOException {
    accounts.recordSuccessfulLogin(authentication.getName());
    HttpSession session = request.getSession(true);
    session.setAttribute(SessionLifetimeFilter.AUTHENTICATED_AT, clock.instant());
    response.sendRedirect("/?access=account");
  }
}

/** Actualiza el bloqueo por contraseña fallida y conserva un mensaje de error genérico. */
@Component
class CitizenLoginFailureHandler implements AuthenticationFailureHandler {
  private final CitizenAccountService accounts;

  CitizenLoginFailureHandler(CitizenAccountService accounts) {
    this.accounts = accounts;
  }

  @Override
  public void onAuthenticationFailure(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      AuthenticationException exception)
      throws java.io.IOException {
    if ("BadCredentialsException".equals(exception.getClass().getSimpleName())) {
      accounts.recordFailedAttempt(request.getParameter("username"));
    }
    response.sendRedirect("/?access=login&error");
  }
}
