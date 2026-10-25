package dev.termsofsurrender.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.analysis.AnalysisEngine;
import dev.termsofsurrender.jobs.AnalysisRepository;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisApiTest {
    private static final String TEXT =
            "This membership renews automatically each year. You must provide written notice thirty days before renewal. A cancellation fee of fifty dollars applies.";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AnalysisRepository repository;
    @Autowired private AnalysisEngine engine;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnalysisStreams streams;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM analyses");
        jdbc.update("DELETE FROM analysis_admissions");
    }

    private Cookie session() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/session"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store, private"))
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("tos_session");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api");
        return cookie;
    }

    private String create(Cookie cookie) throws Exception {
        MvcResult result =
                mvc.perform(
                                multipart("/api/analyses")
                                        .param("text", TEXT)
                                        .param("jurisdiction", "INDIA")
                                        .param("category", "FREELANCE")
                                        .cookie(cookie)
                                        .header("Origin", "http://localhost:3000"))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.status").value("QUEUED"))
                        .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    @Test
    void persistsProcessesAndDeletesPrivateReport() throws Exception {
        Cookie owner = session();
        String id = create(owner);
        var job = repository.claim().orElseThrow();
        assertThat(repository.claim()).isEmpty();
        repository.complete(job.id(), engine.analyze(job.input()));
        mvc.perform(get("/api/analyses/" + id).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.report.mode").value("RULE_BASED_PREVIEW"))
                .andExpect(jsonPath("$.report.findings[0].evidence.quote").isNotEmpty());
        mvc.perform(
                        delete("/api/analyses/" + id)
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/analyses/" + id).cookie(owner)).andExpect(status().isNotFound());
    }

    @Test
    void blocksCrossSessionReadsStreamsAndDeletion() throws Exception {
        String id = create(session());
        Cookie stranger = session();
        mvc.perform(get("/api/analyses/" + id).cookie(stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/" + id + "/events").cookie(stranger))
                .andExpect(status().isNotFound());
        mvc.perform(
                        delete("/api/analyses/" + id)
                                .cookie(stranger)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void requiresTrustedOriginAndValidContext() throws Exception {
        Cookie owner = session();
        mvc.perform(multipart("/api/analyses").param("text", TEXT).cookie(owner))
                .andExpect(status().isForbidden());
        mvc.perform(
                        multipart("/api/analyses")
                                .param("text", TEXT)
                                .cookie(owner)
                                .header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        multipart("/api/analyses")
                                .param("text", TEXT)
                                .param("jurisdiction", "invented")
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesAdmissionLimit() throws Exception {
        Cookie owner = session();
        create(owner);
        create(owner);
        mvc.perform(
                        multipart("/api/analyses")
                                .param("text", TEXT)
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SESSION_LIMIT"));
    }

    @Test
    void removesExpiredDataAndFailsAbandonedJobs() throws Exception {
        Cookie owner = session();
        String id = create(owner);
        repository.claim().orElseThrow();
        jdbc.update(
                "UPDATE analyses SET lease_until = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                id);
        repository.cleanup();
        mvc.perform(get("/api/analyses/" + id).cookie(owner))
                .andExpect(jsonPath("$.errorCode").value("WORKER_TIMEOUT"));
        jdbc.update(
                "UPDATE analyses SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                id);
        mvc.perform(get("/api/analyses/" + id).cookie(owner)).andExpect(status().isNotFound());
        repository.cleanup();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM analyses WHERE id = ?",
                                Integer.class,
                                UUID.fromString(id).toString()))
                .isZero();
    }

    @Test
    void deletingReportsDoesNotResetHourlyAllowance() throws Exception {
        Cookie owner = session();
        for (int index = 0; index < 10; index++) {
            String id = create(owner);
            mvc.perform(
                            delete("/api/analyses/" + id)
                                    .cookie(owner)
                                    .header("Origin", "http://localhost:3000"))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(
                        multipart("/api/analyses")
                                .param("text", TEXT)
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
    }

    @Test
    void deletedJobCannotBeResurrectedByItsWorker() throws Exception {
        Cookie owner = session();
        String id = create(owner);
        var job = repository.claim().orElseThrow();
        mvc.perform(
                        delete("/api/analyses/" + id)
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNoContent());
        repository.complete(job.id(), engine.analyze(job.input()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
    }

    @Test
    void streamsPersistedFinalSnapshotToItsOwner() throws Exception {
        Cookie owner = session();
        String id = create(owner);
        MvcResult stream =
                mvc.perform(get("/api/analyses/" + id + "/events").cookie(owner))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        var job = repository.claim().orElseThrow();
        repository.complete(job.id(), engine.analyze(job.input()));
        streams.publishSnapshots();
        stream.getAsyncResult(2000);
        mvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:snapshot")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    void concurrentWorkersCannotClaimTheSameJob() throws Exception {
        create(session());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks =
                    java.util.stream.IntStream.range(0, 8)
                            .mapToObj(
                                    index ->
                                            (java.util.concurrent.Callable<Boolean>)
                                                    () -> repository.claim().isPresent())
                            .toList();
            int claimed = 0;
            for (var result : executor.invokeAll(tasks)) {
                if (result.get()) {
                    claimed++;
                }
            }
            assertThat(claimed).isEqualTo(1);
        }
    }
}
