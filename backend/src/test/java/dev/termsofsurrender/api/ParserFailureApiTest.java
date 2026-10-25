package dev.termsofsurrender.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.termsofsurrender.documents.IsolatedFileParser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParserFailureApiTest {
    private static final String TEXT =
            "This agreement renews automatically each year unless written notice is received thirty days before renewal.";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private IsolatedFileParser parser;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM analyses");
        jdbc.update("DELETE FROM analysis_admissions");
    }

    @Test
    void workerTimeoutDoesNotPersistAJobOrSilentlyAnalyzeAnEmptyDocument() throws Exception {
        checkFailure(HttpStatus.UNPROCESSABLE_ENTITY, "PARSER_TIMEOUT");
    }

    @Test
    void saturatedWorkersDoNotConsumeAnalysisAdmissionQuota() throws Exception {
        checkFailure(HttpStatus.TOO_MANY_REQUESTS, "PARSER_BUSY");
    }

    @Test
    void crashedWorkerDoesNotPreventPlainTextSubmission() throws Exception {
        checkFailure(HttpStatus.SERVICE_UNAVAILABLE, "PARSER_UNAVAILABLE");
        Cookie owner =
                mvc.perform(get("/api/session")).andReturn().getResponse().getCookie("tos_session");
        mvc.perform(
                        multipart("/api/analyses")
                                .param("text", TEXT)
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.document.sourceType").value("TEXT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class))
                .isEqualTo(1);
    }

    private void checkFailure(HttpStatus failure, String code) throws Exception {
        Cookie owner =
                mvc.perform(get("/api/session")).andReturn().getResponse().getCookie("tos_session");
        when(parser.extract(eq("PDF"), any()))
                .thenThrow(
                        new ApiException(failure, code, "Document extraction could not complete."));
        mvc.perform(
                        multipart("/api/analyses")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "private.pdf",
                                                "application/pdf",
                                                new byte[] {1}))
                                .cookie(owner)
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().is(failure.value()))
                .andExpect(jsonPath("$.code").value(code));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_admissions", Integer.class))
                .isZero();
    }
}
