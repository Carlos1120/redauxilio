package co.redauxilio.publications;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba que el catálogo real se persiste con evidencia y aparece en la consulta pública. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:demo-reports;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "redauxilio.demo.reports-enabled=true"
    })
@AutoConfigureMockMvc
class DemoPublicationSeederTests {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private DemoPublicationSeeder seeder;

  @Test
  void seedsNineIdempotentReportsWithPublicEvidence() throws Exception {
    seeder.run(new DefaultApplicationArguments(new String[0]));
    Integer reports =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication p JOIN citizen_account a ON a.id=p.author_id WHERE a.email='muestras@redauxilio.local'",
            Integer.class);
    Integer images =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_evidence e JOIN citizen_publication p ON p.id=e.publication_id JOIN citizen_account a ON a.id=p.author_id WHERE a.email='muestras@redauxilio.local'",
            Integer.class);
    org.junit.jupiter.api.Assertions.assertEquals(9, reports);
    org.junit.jupiter.api.Assertions.assertEquals(9, images);

    long reportId =
        jdbc.queryForObject(
            "SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id=p.author_id WHERE a.email='muestras@redauxilio.local' AND p.title='Centro de acogida La Esperanza'",
            Long.class);
    mvc.perform(get("/api/publications"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Centro de acogida La Esperanza")))
        .andExpect(content().string(containsString("Hundimiento en el Anillo Vial")));
    mvc.perform(get("/api/publications/{id}/evidence", reportId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/evidence/")));
  }
}
