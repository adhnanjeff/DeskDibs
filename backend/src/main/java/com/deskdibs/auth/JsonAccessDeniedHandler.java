package com.deskdibs.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Every 403 the application emits: authenticated, but not allowed.
 *
 * <p>One fixed sentence for all of them. Which rule refused, which role would have been enough,
 * and which object was being reached for are all facts about the system's shape, and handing them
 * to somebody already established as not entitled to the resource only helps them map it. The
 * detail goes to the log, with the acting user id so it can be traced.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonAccessDeniedHandler.class);

    private static final String MESSAGE = "You do not have permission to perform this action.";

    private final AuthErrorWriter errors;
    private final CurrentUser currentUser;

    public JsonAccessDeniedHandler(AuthErrorWriter errors, CurrentUser currentUser) {
        this.errors = errors;
        this.currentUser = currentUser;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException denial) throws IOException {

        log.debug("403 on {} {} for user {}: {}", request.getMethod(), request.getRequestURI(),
                currentUser.find().map(user -> String.valueOf(user.id())).orElse("anonymous"),
                denial.getMessage());

        errors.write(request, response, HttpStatus.FORBIDDEN.value(), AuthErrorCode.ACCESS_DENIED, MESSAGE);
    }
}
