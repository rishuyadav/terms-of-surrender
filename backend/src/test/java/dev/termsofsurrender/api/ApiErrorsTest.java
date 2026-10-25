package dev.termsofsurrender.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class ApiErrorsTest {
    @Test
    void disconnectedAsyncResponseIsNotRewrittenAsProblemDetails() throws Exception {
        MockMvcBuilders.standaloneSetup(new DisconnectedController())
                .setControllerAdvice(new ApiErrors())
                .build()
                .perform(get("/disconnected"))
                .andExpect(content().string(""));
    }

    @RestController
    static class DisconnectedController {
        @GetMapping("/disconnected")
        String disconnected() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("Synthetic closed SSE response");
        }
    }
}
