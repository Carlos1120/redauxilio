package co.redauxilio.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/** Finaliza la sesión autenticada al cumplir 24 horas, aunque siga recibiendo actividad. */
class SessionLifetimeFilter extends OncePerRequestFilter {
  static final String AUTHENTICATED_AT = "redauxilio.authenticatedAt";
  private static final Duration MAXIMUM_LIFETIME = Duration.ofHours(24);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    Object authenticatedAt = session == null ? null : session.getAttribute(AUTHENTICATED_AT);
    if (request.getUserPrincipal() != null
        && authenticatedAt instanceof Instant startedAt
        && !Instant.now().isBefore(startedAt.plus(MAXIMUM_LIFETIME))) {
      session.invalidate();
      response.sendRedirect("/login?expired");
      return;
    }
    chain.doFilter(request, response);
  }
}
