package com.splitwise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests through the HTTP layer, security and an in-memory H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
class SplitwiseApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    record TestUser(long id, String email, String token) {
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/groups").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void duplicateSignupAndWrongPasswordAreRejected() throws Exception {
        TestUser user = signup("Dup");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Dup", "email", user.email(), "password", "password123"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", user.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", user.email(), "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void fullFlowSimplifiesChainedDebts() throws Exception {
        TestUser a = signup("Asha");
        TestUser b = signup("Bala");
        TestUser c = signup("Chirag");

        long groupId = createGroup(a, "Goa trip", b.email(), c.email());

        // B pays 100 for A only  =>  A owes B 100
        addExpense(b, groupId, Map.of("description", "Cab", "amount", 100, "splitType", "EXACT",
                "splits", new Object[]{Map.of("userId", a.id(), "value", 100)}))
                .andExpect(status().isCreated());
        // C pays 100 for B only  =>  B owes C 100
        addExpense(c, groupId, Map.of("description", "Snacks", "amount", 100, "splitType", "PERCENTAGE",
                "splits", new Object[]{Map.of("userId", b.id(), "value", 100)}))
                .andExpect(status().isCreated());

        JsonNode balances = getJson(a, "/api/groups/" + groupId + "/balances");
        assertThat(balances.get("pairwiseDebts")).hasSize(2);
        JsonNode simplified = balances.get("simplifiedDebts");
        assertThat(simplified).hasSize(1);
        assertThat(simplified.get(0).get("from").get("id").asLong()).isEqualTo(a.id());
        assertThat(simplified.get(0).get("to").get("id").asLong()).isEqualTo(c.id());
        assertThat(simplified.get(0).get("amount").decimalValue()).isEqualByComparingTo("100");

        // A settles up with C, so everything is square
        mvc.perform(post("/api/groups/" + groupId + "/settlements").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("payeeUserId", c.id(), "amount", 100))))
                .andExpect(status().isCreated());

        JsonNode after = getJson(a, "/api/groups/" + groupId + "/balances");
        assertThat(after.get("simplifiedDebts")).isEmpty();
        after.get("balances").forEach(bal -> assertThat(bal.get("balance").decimalValue()).isEqualByComparingTo("0"));
    }

    @Test
    void equalSplitDefaultsToAllMembersAndShowsInHistory() throws Exception {
        TestUser a = signup("Arun");
        TestUser b = signup("Bhavna");
        TestUser c = signup("Charu");
        long groupId = createGroup(a, "Flat", b.email(), c.email());

        addExpense(a, groupId, Map.of("description", "Groceries", "amount", 100, "splitType", "EQUAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits.length()").value(3));

        JsonNode history = getJson(b, "/api/groups/" + groupId + "/expenses");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("description").asText()).isEqualTo("Groceries");

        JsonNode myBalance = getJson(a, "/api/users/me/balance");
        assertThat(myBalance.get("totalBalance").decimalValue()).isEqualByComparingTo("66.66");
    }

    @Test
    void invalidSplitsAreRejected() throws Exception {
        TestUser a = signup("Anil");
        TestUser b = signup("Bina");
        long groupId = createGroup(a, "Office lunch", b.email());

        addExpense(a, groupId, Map.of("description", "Pizza", "amount", 100, "splitType", "PERCENTAGE",
                "splits", new Object[]{Map.of("userId", a.id(), "value", 60), Map.of("userId", b.id(), "value", 30)}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("100")));
    }

    @Test
    void nonMembersCannotSeeGroup() throws Exception {
        TestUser owner = signup("Owner");
        TestUser outsider = signup("Outsider");
        long groupId = createGroup(owner, "Private");

        mvc.perform(get("/api/groups/" + groupId).header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/groups/" + groupId + "/balances").header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private TestUser signup(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        String response = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return new TestUser(node.get("user").get("id").asLong(), email, node.get("token").asText());
    }

    private long createGroup(TestUser owner, String name, String... memberEmails) throws Exception {
        String response = mvc.perform(post("/api/groups").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "memberEmails", memberEmails))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions addExpense(TestUser caller, long groupId, Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/groups/" + groupId + "/expenses").header("Authorization", "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    private JsonNode getJson(TestUser caller, String path) throws Exception {
        String response = mvc.perform(get(path).header("Authorization", "Bearer " + caller.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
