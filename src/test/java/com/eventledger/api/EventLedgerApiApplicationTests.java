package com.eventledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EventLedgerApiApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

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
    void listEventsSupportsPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            String event = "{" +
                    "\"eventId\": \"evt-pag-" + i + "\"," +
                    "\"accountId\": \"acct-pag\"," +
                    "\"type\": \"CREDIT\"," +
                    "\"amount\": " + (10 * i) + "," +
                    "\"currency\": \"USD\"," +
                    "\"eventTimestamp\": \"2026-05-15T14:0" + i + ":00Z\"" +
                    "}";
            mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(event))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/events")
                        .param("account", "acct-pag")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value("evt-pag-1"))
                .andExpect(jsonPath("$[1].eventId").value("evt-pag-2"));

        mockMvc.perform(get("/events")
                        .param("account", "acct-pag")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value("evt-pag-3"))
                .andExpect(jsonPath("$[1].eventId").value("evt-pag-4"));
    }

    @Test
    void concurrentDuplicateEventSubmissionsAreHandledGracefully() throws Exception {
        String payload = "{" +
                "\"eventId\": \"evt-concurrent\"," +
                "\"accountId\": \"acct-concurrent\"," +
                "\"type\": \"CREDIT\"," +
                "\"amount\": 100.0," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:00:00Z\"" +
                "}";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> bodies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    ResponseEntity<String> response = restTemplate.postForEntity("/events", entity, String.class);
                    statuses.add(response.getStatusCodeValue());
                    bodies.add(response.getBody());
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(exceptions.isEmpty(), "No thread exceptions should occur");
        assertTrue(statuses.contains(201));
        assertTrue(statuses.contains(200));
        assertEquals(2, bodies.size());
        bodies.forEach(body -> assertTrue(body.contains("\"eventId\":\"evt-concurrent\"")));
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
