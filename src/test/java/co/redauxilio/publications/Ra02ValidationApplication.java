package co.redauxilio.publications;

import co.redauxilio.RedAuxilioApplication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Arranque exclusivo del classpath de pruebas: 1.000 publicaciones ficticias y medición interna. No
 * modifica la lista de producción ni agrega persistencia o rutas de administración.
 */
public class Ra02ValidationApplication {
  public static void main(String[] args) {
    SpringApplication.run(new Class<?>[] {RedAuxilioApplication.class, Fixture.class}, args);
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Fixture {
    /**
     * Distribuye cuatro categorías en un rectángulo ficticio de Villavicencio. Cada décimo caso
     * está cerrado y cada vigésimo está oculto: 900 activos y 50 cerrados visibles, 50 ocultos.
     */
    @Bean
    @Primary
    PublicationService loadValidationPublications() {
      List<PublicationService.Entry> entries = new ArrayList<>();
      String[] categories = {"ASSISTANCE", "ROAD", "MISSING_PERSON", "HELP_REQUEST"};
      String[] statuses = {"Disponible", "Bloqueada", "Sin localizar", "Abierta"};
      for (int i = 1; i <= 1000; i++) {
        int category = (i - 1) % categories.length;
        boolean closed = i % 10 == 0;
        entries.add(
            new PublicationService.Entry(
                new PublicationService.Publication(
                    i,
                    categories[category],
                    "Reporte sintético de carga " + i,
                    "Ubicación ficticia; no usar en emergencias",
                    closed ? "Cerrado" : statuses[category],
                    "REPORTADA",
                    "2026-10-06T15:00:00Z",
                    4.13 + ((i - 1) / 50) * 0.0015,
                    -73.65 + ((i - 1) % 50) * 0.001,
                    "Dato generado únicamente en ámbito de prueba para RA-02.",
                    "2026-10-05T15:00:00Z",
                    closed),
                i % 20 != 0));
      }
      return new PublicationService(entries);
    }

    /**
     * Cronometra filtro/DispatcherServlet y serialización JSON antes de enviar el cuerpo. Excluye
     * cola previa al filtro, transferencia de red y renderizado del navegador. Buffer y cabecera
     * existen solo en pruebas; su sobrecarga hace conservadora esta muestra interna.
     */
    @Bean
    OncePerRequestFilter measureValidationServerTime() {
      return new OncePerRequestFilter() {
        @Override
        protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
          if (request.getRequestURI().equals("/")) {
            // Rechazo real del navegador por CSP, aislado al fixture y a la URL de prueba.
            // cdn-failure bloquea Leaflet; tile-failure permite biblioteca pero impide mapas
            // externos.
            String failure = request.getParameter("validation");
            if ("cdn-failure".equals(failure)) {
              response.setHeader(
                  "Content-Security-Policy",
                  "script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:");
            } else if ("tile-failure".equals(failure)) {
              response.setHeader("Content-Security-Policy", "img-src 'self' data:");
            }
            // Test-only: observa el primer frame con lista y marcadores desde inicio de navegación.
            ContentCachingResponseWrapper page = new ContentCachingResponseWrapper(response);
            chain.doFilter(request, page);
            String html =
                new String(page.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            String probe =
                """
                <script>
                new MutationObserver(function(changes, observer) {
                  const map = document.getElementById('report-map');
                  if (map && map.querySelector('.report-marker') &&
                      document.querySelector('#publications article')) {
                    observer.disconnect();
                    requestAnimationFrame(function() {
                      requestAnimationFrame(function() {
                        map.dataset.firstResultsMillis = performance.now().toFixed(1);
                        map.dataset.validationUserAgent = navigator.userAgent;
                      });
                    });
                  }
                }).observe(document.documentElement, {subtree:true,childList:true});
                </script>
                """;
            byte[] instrumented =
                html.replace("<head>", "<head>" + probe)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            response.setContentLength(instrumented.length);
            response.getOutputStream().write(instrumented);
            return;
          }
          if (!request.getRequestURI().startsWith("/api/publications")) {
            chain.doFilter(request, response);
            return;
          }
          ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
          long start = System.nanoTime();
          chain.doFilter(request, buffered);
          double millis = (System.nanoTime() - start) / 1_000_000.0;
          buffered.setHeader(
              "Server-Timing", "ra02;dur=" + String.format(java.util.Locale.ROOT, "%.3f", millis));
          buffered.copyBodyToResponse();
        }
      };
    }
  }
}
