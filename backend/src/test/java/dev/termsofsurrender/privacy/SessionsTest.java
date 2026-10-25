package dev.termsofsurrender.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.api.ApiException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionsTest {
    private final Sessions sessions =
            new Sessions(
                    new AppProperties(
                            List.of("https://contracts.example"),
                            true,
                            24,
                            2,
                            10,
                            50,
                            false,
                            new AppProperties.Model(false, "https://example.com", "", "")));

    @Test
    void usesSecurePrivateCookiesAndStoresOnlyTheirHash() {
        var response = new MockHttpServletResponse();
        String owner = sessions.establish(new MockHttpServletRequest(), response);
        assertThat(response.getHeader("Set-Cookie"))
                .contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/api");
        String token = response.getCookie("tos_session").getValue();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(owner).hasSize(64).isNotEqualTo(token);
        var nextRequest = new MockHttpServletRequest();
        nextRequest.setCookies(new Cookie("tos_session", token));
        assertThat(sessions.require(nextRequest)).isEqualTo(owner);
        assertThat(sessions.establish(nextRequest, new MockHttpServletResponse())).isEqualTo(owner);
    }

    @Test
    void rejectsMissingAndMalformedTokensWithoutRevealingReports() {
        assertThatThrownBy(() -> sessions.require(new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class);
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("tos_session", "short-token"));
        assertThatThrownBy(() -> sessions.require(request)).isInstanceOf(ApiException.class);
    }
}
