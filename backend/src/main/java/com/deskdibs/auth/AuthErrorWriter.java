package com.deskdibs.auth;

import com.deskdibs.common.OfficeClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes a refusal straight onto the response, from inside the filter chain.
 *
 * <p>A 401 or a 403 is decided before any handler runs, so there is no {@code @RestController} to
 * return a body and no message converter in play. Left alone the container fills the gap with its
 * own error page — HTML, and in a misconfigured deployment a stack trace. This writes the JSON
 * itself so that never happens.
 *
 * <p>The response is committed here deliberately: no {@code sendError}, which would hand control
 * back to the container's error dispatch and undo the point of the exercise.
 */
@Component
public class AuthErrorWriter {

    private final ObjectMapper objectMapper;
    private final OfficeClock officeClock;

    public AuthErrorWriter(ObjectMapper objectMapper, OfficeClock officeClock) {
        this.objectMapper = objectMapper;
        this.officeClock = officeClock;
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      int status,
                      AuthErrorCode code,
                      String message) throws IOException {

        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                new AuthErrorResponse(code, message, request.getRequestURI(), officeClock.timestamp()));
    }
}
