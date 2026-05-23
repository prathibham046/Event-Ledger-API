package com.eventledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventLedgerApiApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createEventAndRetrieveIt() throws Exception {
        String payload = "{" +
                "\"eventId\": \"evt-001\"," +
                "\"accountId\": \"acct-123\"," +
                "\"type\": \"CREDIT\"," +
                "\"amount\": 150.00," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:02:11Z\"," +
                "\"metadata\": {\"source\": \"mainframe-batch\", \"batchId\": \"B-9042\"}" +
                "}";

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.accountId").value("acct-123"))
                .andExpect(jsonPath("$.type").value("CREDIT"));

        mockMvc.perform(get("/events/evt-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.metadata.source").value("mainframe-batch"));
    }

    @Test
    void duplicateSubmissionIsIdempotent() throws Exception {
        String payload = "{" +
                "\"eventId\": \"evt-002\"," +
                "\"accountId\": \"acct-123\"," +
                "\"type\": \"DEBIT\"," +
                "\"amount\": 25.0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:05:00Z\"" +
                "}";

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-002"));
    }

    @Test
    void outOfOrderEventsStillSortedAndBalanceComputedCorrectly() throws Exception {
        String event3 = "{" +
                "\"eventId\": \"evt-003\"," +
                "\"accountId\": \"acct-456\"," +
                "\"type\": \"CREDIT\"," +
                "\"amount\": 200.0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:10:00Z\"" +
                "}";
        String event4 = "{" +
                "\"eventId\": \"evt-004\"," +
                "\"accountId\": \"acct-456\"," +
                "\"type\": \"CREDIT\"," +
                "\"amount\": 100.0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:00:00Z\"" +
                "}";
        String event5 = "{" +
                "\"eventId\": \"evt-005\"," +
                "\"accountId\": \"acct-456\"," +
                "\"type\": \"DEBIT\"," +
                "\"amount\": 50.0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:05:00Z\"" +
                "}";

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(event3)).andExpect(status().isCreated());
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(event4)).andExpect(status().isCreated());
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(event5)).andExpect(status().isCreated());

        mockMvc.perform(get("/events").param("account", "acct-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].eventId").value("evt-004"))
                .andExpect(jsonPath("$[1].eventId").value("evt-005"))
                .andExpect(jsonPath("$[2].eventId").value("evt-003"));

        mockMvc.perform(get("/accounts/acct-456/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.0));
    }

    @Test
    void validationErrorsReturnBadRequest() throws Exception {
        String invalidAmount = "{" +
                "\"eventId\": \"evt-006\"," +
                "\"accountId\": \"acct-789\"," +
                "\"type\": \"CREDIT\"," +
                "\"amount\": 0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:00:00Z\"" +
                "}";

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(invalidAmount))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("amount")));

        String invalidType = "{" +
                "\"eventId\": \"evt-007\"," +
                "\"accountId\": \"acct-789\"," +
                "\"type\": \"TRANSFER\"," +
                "\"amount\": 50.0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:00:00Z\"" +
                "}";

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(invalidType))
                .andExpect(status().isBadRequest());
    }
}
