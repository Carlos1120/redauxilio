package co.redauxilio.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DraftKeyControllerTests {
  @Autowired private MockMvc mvc;
  @Autowired private CitizenAccountRepository accounts;
  @Autowired private ObjectMapper mapper;

  @Test
  void keysArePrivateStableAndAccountSpecific() throws Exception {
    String a = "draft-a-" + UUID.randomUUID() + "@example.invalid";
    String b = "draft-b-" + UUID.randomUUID() + "@example.invalid";
    accounts.create(a, "A", "hash", Instant.now());
    accounts.create(b, "B", "hash", Instant.now());
    mvc.perform(get("/api/my/draft-key")).andExpect(status().isUnauthorized());
    String first = mvc.perform(get("/api/my/draft-key").with(user(a)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String again = mvc.perform(get("/api/my/draft-key").with(user(a)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String other = mvc.perform(get("/api/my/draft-key").with(user(b)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(mapper.readTree(first).get("key"), mapper.readTree(again).get("key"));
    assertNotEquals(mapper.readTree(first).get("key"), mapper.readTree(other).get("key"));
    assertNotEquals(mapper.readTree(first).get("owner"), mapper.readTree(other).get("owner"));
  }
}
