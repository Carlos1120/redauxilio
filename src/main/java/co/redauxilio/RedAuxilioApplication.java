package co.redauxilio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque del servidor. Spring descubre los controladores y servicios del paquete
 * co.redauxilio, configura la aplicación y pone a escuchar su servidor HTTP integrado.
 */
@SpringBootApplication
public class RedAuxilioApplication {
  public static void main(String[] args) {
    SpringApplication.run(RedAuxilioApplication.class, args);
  }
}
