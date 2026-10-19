package com.connectify.controller;

import com.connectify.security.DatabaseUserDetailsService;
import com.connectify.security.GateLoginThrottle;
import com.connectify.security.GateSecurityConfig;
import com.connectify.security.SecurityConfig;
import com.connectify.service.EventGateAccessService;
import com.connectify.service.EventGateAccessService.GateSession;
import com.connectify.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses both production security chains; prod only suppresses the demo-data runner, with no data-source setup. */
@WebMvcTest(controllers = {LoginController.class, TicketController.class})
@ContextConfiguration(classes = {LoginController.class, TicketController.class, GateSecurityConfig.class, SecurityConfig.class})
@ActiveProfiles("prod")
class LoginGateCsrfTemplateTest {
    @Autowired private MockMvc mvc;
    @MockBean private DatabaseUserDetailsService users;
    @MockBean private EventGateAccessService gates;
    @MockBean private TicketService tickets;
    @MockBean private GateLoginThrottle throttle;

    @Test
    void theActualLoginModalTokenIsAcceptedByTheSeparateGateChainWithoutAnAccountRole() throws Exception {
        var page = mvc.perform(get("/login"))
                .andExpect(status().isOk()).andExpect(view().name("auth/client-login"))
                .andReturn();
        String html = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        var formMatcher = Pattern.compile("<form\\b[^>]*action=\"/tickets/validate/open\"[^>]*>[\\s\\S]*?</form>")
                .matcher(html);
        assertThat(formMatcher.find()).isTrue();
        String form = formMatcher.group();
        var tokenMatcher = Pattern.compile("<input\\b[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"[^>]*>")
                .matcher(form);
        assertThat(tokenMatcher.find()).isTrue();
        String renderedToken = tokenMatcher.group(1);
        assertThat(form).contains("name=\"eventCode\"", "name=\"gatePassword\"").doesNotContain("name=\"eventId\"");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        assertThat(session).isNotNull();

        GateSession grant = new GateSession(22L, 2202L, 1L, Instant.parse("2099-01-01T00:00:00Z"));
        when(throttle.allows("127.0.0.1")).thenReturn(true);
        when(gates.open("EVT-22", "fixture-door-password")).thenReturn(grant);

        // Submit the actual token emitted on /login, not a security-test csrf() replacement.
        mvc.perform(post("/tickets/validate/open").session(session)
                        .param("_csrf", renderedToken)
                        .param("eventCode", "EVT-22").param("gatePassword", "fixture-door-password"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tickets/validate"));
        assertThat(session.getAttribute(EventGateAccessService.SESSION_KEY)).isSameAs(grant);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        verify(gates).open("EVT-22", "fixture-door-password");
        verifyNoInteractions(users, tickets);
    }
}
