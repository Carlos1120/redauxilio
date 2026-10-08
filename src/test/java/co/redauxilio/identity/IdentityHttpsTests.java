package co.redauxilio.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba el HTTPS obligatorio en producción y el esquema HTTPS comunicado por el proxy. */
@SpringBootTest(
    properties = {
      "redauxilio.security.force-https=true",
      "server.forward-headers-strategy=framework"
    })
@AutoConfigureMockMvc
class IdentityHttpsTests {
  @Autowired private MockMvc mvc;

  @Test
  void redirectsUnsecuredRequestsToHttps() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://")));
  }

  @Test
  void acceptsHttpsReportedByTheReverseProxy() throws Exception {
    mvc.perform(get("/").header("X-Forwarded-Proto", "https")).andExpect(status().isOk());
  }
}
