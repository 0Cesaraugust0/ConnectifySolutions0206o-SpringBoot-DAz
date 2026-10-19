package com.connectify.service;

import com.connectify.security.GateSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifies the actual gate security chain independently of database and account login services. */
@WebMvcTest(controllers = GateSecurityFilterTest.ProbeController.class)
@ContextConfiguration(classes = {GateSecurityConfig.class, GateSecurityFilterTest.ProbeController.class,
        GateSecurityFilterTest.SessionConfiguration.class})
class GateSecurityFilterTest {
    @Autowired private MockMvc mvc;
    @Autowired private SessionRegistry sessions;
    @MockBean private UserDetailsService users;

    @Test void doorAccessDoesNotRequireAnAccountLoginButUnsafeRequestsRequireCsrf() throws Exception {
        mvc.perform(get("/tickets")).andExpect(status().isOk());
        mvc.perform(post("/tickets/gate")).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/gate").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/tickets/validate")).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/validate").with(csrf())).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "ORGANIZER", "DESIGNER", "DEVELOPER", "GATE_AGENT"})
    void otherAccountRolesCannotConfigureEventDoors(String role) throws Exception {
        mvc.perform(get("/admin/events/22/gate").with(user("test").roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/events/22/gate").with(user("test").roles(role)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void anonymousUsersAreSentToLoginForDoorAdministration() throws Exception {
        mvc.perform(get("/admin/events/22/gate"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test void administratorCanConfigureDoorsOnlyWithCsrfProtection() throws Exception {
        mvc.perform(get("/admin/events/22/gate").with(user("admin-test").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/events/22/gate").with(user("admin-test").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/events/22/gate").with(user("admin-test").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/dashboard/admin/events/22/gate").with(user("admin-test").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/events/22/gate", "/dashboard/admin/events/22/gate"})
    void revokedAdministratorSessionIsBlockedBeforeReachingEitherAdministrationHandler(String route) throws Exception {
        MockHttpSession session = new MockHttpSession();
        var admin = User.withUsername("revoked-admin-test").password("unused").roles("ADMIN").build();
        sessions.registerNewSession(session.getId(), admin);
        sessions.getSessionInformation(session.getId()).expireNow();

        mvc.perform(get(route).session(session).with(user(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?accessUpdated"))
                .andExpect(result -> assertThat(result.getHandler()).isNull());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void aRegisteredActiveAdministratorSessionStillReachesTheHandler() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var admin = User.withUsername("active-admin-test").password("unused").roles("ADMIN").build();
        sessions.registerNewSession(session.getId(), admin);

        mvc.perform(get("/admin/events/22/gate").session(session).with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
        assertThat(session.isInvalid()).isFalse();
    }

    @TestConfiguration
    static class SessionConfiguration {
        @Bean SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/tickets", "/admin/events/22/gate", "/dashboard/admin/events/22/gate"}) String read() { return "OK"; }
        @PostMapping({"/tickets/gate", "/tickets/validate", "/admin/events/22/gate", "/dashboard/admin/events/22/gate"})
        String write() { return "OK"; }
    }
}
