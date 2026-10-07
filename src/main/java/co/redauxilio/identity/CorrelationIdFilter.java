package co.redauxilio.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asocia cada petición con un identificador seguro para localizar fallos en los logs del servidor.
 */
@Component
class CorrelationIdFilter extends OncePerRequestFilter {
  static final String HEADER_NAME = "X-Correlation-ID";
  static final String MDC_KEY = "correlationId";
  private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = validUuid(request.getHeader(HEADER_NAME));
    if (correlationId == null) correlationId = UUID.randomUUID().toString();
    response.setHeader(HEADER_NAME, correlationId);
    request.setAttribute(MDC_KEY, correlationId);
    MDC.put(MDC_KEY, correlationId);
    try {
      chain.doFilter(request, response);
      if (response.getStatus() >= 500) {
        LOGGER.error(
            "La solicitud respondió HTTP {}: {} {}",
            response.getStatus(),
            request.getMethod(),
            request.getRequestURI());
      }
    } catch (IOException | ServletException | RuntimeException exception) {
      LOGGER.error(
          "Error en solicitud {} {}", request.getMethod(), request.getRequestURI(), exception);
      throw exception;
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private String validUuid(String candidate) {
    if (candidate == null) return null;
    try {
      String canonical = UUID.fromString(candidate).toString();
      return canonical.equalsIgnoreCase(candidate) ? canonical : null;
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
