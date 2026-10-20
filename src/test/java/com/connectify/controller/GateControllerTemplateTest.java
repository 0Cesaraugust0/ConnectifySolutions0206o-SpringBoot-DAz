package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.EventGateAccess;
import com.connectify.entity.Ticket;
import com.connectify.security.GateLoginThrottle;
import com.connectify.security.GateSecurityConfig;
import com.connectify.service.EventGateAccessService;
import com.connectify.service.EventGateAccessService.GateContext;
import com.connectify.service.EventGateAccessService.GateSession;
import com.connectify.service.EventGateAccessService.GateSlot;
import com.connectify.service.EventService;
import com.connectify.service.TicketService;
import com.connectify.service.TicketService.GateValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Renders the production Thymeleaf templates with the real gate security chain and fixture-only services. */
@WebMvcTest(controllers = {AdminEventController.class, TicketController.class})
@ContextConfiguration(classes = {AdminEventController.class, TicketController.class, GateSecurityConfig.class})
class GateControllerTemplateTest {
    private static final String LEGACY_SECRET = "fixture-legacy-password-never-render";
    private static final String HASH = "$2a$10$fixtureHashNeverRenderIntoAnyGatePage000000000000000000";
    private static final String CODE = "QR-FIXTURE-22";
    private static final GateSession GRANT =
            new GateSession(22L, 2202L, 3L, Instant.parse("2099-01-01T00:00:00Z"));

    @Autowired private MockMvc mvc;
    @MockBean private EventService events;
    @MockBean private EventGateAccessService gates;
    @MockBean private TicketService tickets;
    @MockBean private GateLoginThrottle throttle;
    @MockBean private UserDetailsService users;
    @MockBean private SessionRegistry sessionRegistry;

    @Test
    void administratorGetsFourDoorFormsWithCsrfAndNoStoredPasswords() throws Exception {
        Event event = event();
        when(events.findById(22L)).thenReturn(Optional.of(event));
        when(gates.slots(22L)).thenReturn(List.of(
                new GateSlot(1, "Acceso principal", true, true),
                new GateSlot(2, "Acceso norte", true, false),
                new GateSlot(3, "Puerta 3", false, true),
                new GateSlot(4, "Puerta 4", false, true)));

        String html = mvc.perform(get("/admin/events/22/gate-access").with(user("fixture-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/gate-access"))
                .andExpect(model().attribute("event", event))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> forms = elements(html, "form");
        assertThat(forms).hasSize(5);
        forms.forEach(GateControllerTemplateTest::assertPostFormHasCsrf);
        List<String> doorForms = forms.stream()
                .filter(form -> "/admin/events/22/gate-access".equals(attribute(form, "action"))).toList();
        assertThat(doorForms).hasSize(4);
        for (int slot = 1; slot <= 4; slot++) {
            String form = doorForms.get(slot - 1);
            assertThat(attribute(input(form, "slot"), "value")).isEqualTo(Integer.toString(slot));
            String password = input(form, "gatePassword");
            assertThat(attribute(password, "type")).isEqualTo("password");
            assertThat(attribute(password, "value")).isNullOrEmpty();
            assertThat(password.contains("required")).isEqualTo(slot >= 3);
        }
        assertThat(forms.stream().filter(form -> "/admin/events/22/gate-code".equals(attribute(form, "action"))))
                .hasSize(1);
        assertThat(html).contains("Encuentro fixture", "Acceso principal", "Acceso norte",
                        "Habilitada", "Desactivada", "Sin configurar", "value=\"EVT-22\"")
                .doesNotContain(LEGACY_SECRET, HASH, "credentialHash", "th:each", "th:value");

        // Safe fixture preview, useful for inspecting the real rendered administration page.
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "gate-admin-preview.html"), html, StandardCharsets.UTF_8);
    }

    @Test
    void actualAdminSaveRequiresCsrfAndUsesTheEventFromThePath() throws Exception {
        mvc.perform(post("/admin/events/22/gate-access").with(user("fixture-admin").roles("ADMIN"))
                        .param("slot", "2").param("label", "Acceso norte").param("gatePassword", "fixture-password-new")
                        .param("active", "true").param("eventId", "999"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(gates);

        mvc.perform(post("/admin/events/22/gate-access").with(user("fixture-admin").roles("ADMIN")).with(csrf())
                        .param("slot", "2").param("label", "Acceso norte").param("gatePassword", "fixture-password-new")
                        .param("active", "true").param("eventId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/events/22/gate-access"))
                .andExpect(flash().attributeExists("gateSuccess"));
        verify(gates).saveSlot(22L, 2, "Acceso norte", "fixture-password-new", true);
        verifyNoMoreInteractions(gates);
    }

    @Test
    void closedDoorRendersOnlyTheCredentialFormAndDoesNotAcceptAnEventSelector() throws Exception {
        when(gates.resolve(null)).thenReturn(Optional.empty());

        String html = mvc.perform(get("/tickets/validate"))
                .andExpect(status().isOk()).andExpect(view().name("tickets/validate"))
                .andExpect(model().attribute("gateUnlocked", false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> forms = elements(html, "form");
        assertThat(forms).hasSize(1);
        String form = forms.getFirst();
        assertThat(attribute(form, "action")).isEqualTo("/tickets/validate/open");
        assertPostFormHasCsrf(form);
        assertThat(attribute(input(form, "gatePassword"), "type")).isEqualTo("password");
        assertThat(input(form, "eventCode")).isNotEmpty();
        assertThat(html).contains("Abrir control de puerta")
                .doesNotContain("name=\"eventId\"", "name=\"code\"", "gate-scan-form", LEGACY_SECRET, HASH);
        verifyNoInteractions(tickets);
    }

    @Test
    void openingTheDoorRotatesTheSessionAndUnlocksTheScannerWithOnlyTheTicketCode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String oldSessionId = session.getId();
        when(throttle.allows("127.0.0.1")).thenReturn(true);
        when(gates.open("EVT-22", "fixture-door-password")).thenReturn(GRANT);
        when(gates.resolve(GRANT)).thenReturn(Optional.of(context()));

        mvc.perform(post("/tickets/validate/open").session(session).with(csrf())
                        .param("eventCode", "EVT-22").param("gatePassword", "fixture-door-password")
                        .param("eventId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/validate"));
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        assertThat(session.getAttribute(EventGateAccessService.SESSION_KEY)).isSameAs(GRANT);
        verify(gates).open("EVT-22", "fixture-door-password");
        verify(throttle).succeeded("127.0.0.1");

        String html = mvc.perform(get("/tickets/validate").session(session))
                .andExpect(status().isOk()).andExpect(model().attribute("gateUnlocked", true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> forms = elements(html, "form");
        assertThat(forms).hasSize(2);
        forms.forEach(GateControllerTemplateTest::assertPostFormHasCsrf);
        String scan = forms.stream().filter(form -> "/tickets/validate/consult".equals(attribute(form, "action")))
                .findFirst().orElseThrow();
        assertThat(elements(scan, "input").stream().map(tag -> attribute(tag, "name")))
                .containsExactlyInAnyOrder("code", "_csrf");
        assertThat(scan).contains("formaction=\"/tickets/validate\"");
        assertThat(html).contains("Encuentro fixture", "Acceso norte", "Consultar", "Validar y registrar ingreso",
                        "id=\"gateScanStart\"", "id=\"gateCameraVideo\"", "playsinline",
                        "/js/gate-scanner.js?v=camera-3")
                .doesNotContain("gateManualEntry", "name=\"eventId\"", "name=\"gatePassword\"", LEGACY_SECRET, HASH, "fixture-door-password");
        Files.writeString(Path.of("target", "gate-scanner-preview.html"), html, StandardCharsets.UTF_8);
        verifyNoInteractions(tickets);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "999")
    void validationUsesOnlyTheGrantedEventAndRendersTheUsedInstantInLima(String clientEventId) throws Exception {
        MockHttpSession session = unlockedSession();
        Ticket ticket = ticket();
        ticket.setUsed(true);
        ticket.setUsedAt(Instant.parse("2026-10-19T03:04:05Z"));
        ticket.setUsedGateName("Acceso norte");
        GateValidationResult result = GateValidationResult.valid(ticket);
        when(gates.resolve(GRANT)).thenReturn(Optional.of(context()));
        when(tickets.validateTicketForEvent(CODE, 22L, "Acceso norte")).thenReturn(result);

        var request = post("/tickets/validate").session(session).with(csrf())
                .param("code", CODE).param("gateName", "Puerta falsa");
        if (clientEventId != null) request.param("eventId", clientEventId);
        String html = mvc.perform(request)
                .andExpect(status().isOk()).andExpect(view().name("tickets/validate"))
                .andExpect(model().attribute("gateUnlocked", true))
                .andExpect(model().attribute("result", result))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Ingreso registrado", "Asistente fixture", "Acceso norte", "18/10/2026 22:04:05")
                .doesNotContain("19/10/2026 03:04:05", "Puerta falsa", LEGACY_SECRET, HASH);
        verify(tickets).validateTicketForEvent(CODE, 22L, "Acceso norte");
        verifyNoMoreInteractions(tickets);
        elements(html, "form").forEach(GateControllerTemplateTest::assertPostFormHasCsrf);
    }

    @Test
    void consultationUsesTheGrantedEventAndNeverCallsAdmission() throws Exception {
        MockHttpSession session = unlockedSession();
        Ticket ticket = ticket();
        GateValidationResult result = GateValidationResult.available(ticket);
        when(gates.resolve(GRANT)).thenReturn(Optional.of(context()));
        when(tickets.consultTicketForEvent(CODE, 22L)).thenReturn(result);

        String html = mvc.perform(post("/tickets/validate/consult").session(session).with(csrf())
                        .param("code", CODE))
                .andExpect(status().isOk()).andExpect(model().attribute("gateUnlocked", true))
                .andExpect(model().attribute("result", result))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Entrada disponible", "Asistente fixture", "Sin ingreso registrado")
                .doesNotContain("<dt>Hora de ingreso</dt>");
        assertThat(ticket.isUsed()).isFalse();
        assertThat(ticket.getUsedAt()).isNull();
        verify(tickets).consultTicketForEvent(CODE, 22L);
        verifyNoMoreInteractions(tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tickets/validate", "/tickets/validate/consult"})
    void clientEventIdCannotBypassARevokedDoorSession(String endpoint) throws Exception {
        MockHttpSession session = unlockedSession();
        when(gates.resolve(GRANT)).thenReturn(Optional.empty());

        String html = mvc.perform(post(endpoint).session(session).with(csrf())
                        .param("code", CODE).param("eventId", "22"))
                .andExpect(status().isOk()).andExpect(model().attribute("gateUnlocked", false))
                .andExpect(model().attributeExists("gateError"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(session.getAttribute(EventGateAccessService.SESSION_KEY)).isNull();
        assertThat(html).contains("Abrir control de puerta").doesNotContain("gate-scan-form", "Asistente fixture");
        verifyNoInteractions(tickets);
    }

    private static Event event() {
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", 22L);
        event.setTitle("Encuentro fixture");
        event.setGateAccessCode("EVT-22");
        event.setGatePassword(LEGACY_SECRET);
        return event;
    }

    private static GateContext context() {
        Event event = event();
        EventGateAccess gate = new EventGateAccess();
        ReflectionTestUtils.setField(gate, "id", 2202L);
        gate.setEvent(event);
        gate.setSlot(2);
        gate.setLabel("Acceso norte");
        gate.setCredentialHash(HASH);
        return new GateContext(event, gate);
    }

    private static Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setCode(CODE);
        ticket.setEvent(event());
        ticket.setAttendeeName("Asistente fixture");
        return ticket;
    }

    private static MockHttpSession unlockedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EventGateAccessService.SESSION_KEY, GRANT);
        return session;
    }

    private static void assertPostFormHasCsrf(String form) {
        assertThat(attribute(form, "method")).isEqualToIgnoringCase("post");
        assertThat(attribute(input(form, "_csrf"), "type")).isEqualTo("hidden");
        assertThat(attribute(input(form, "_csrf"), "value")).isNotBlank();
    }

    private static String input(String html, String name) {
        return elements(html, "input").stream().filter(tag -> name.equals(attribute(tag, "name")))
                .findFirst().orElseThrow(() -> new AssertionError("Missing input: " + name));
    }

    private static String attribute(String element, String name) {
        var matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"")
                .matcher(element.substring(0, element.indexOf('>') + 1));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> elements(String html, String tag) {
        String expression = tag.equals("input") ? "<input\\b[^>]*>" : "<" + tag + "\\b[^>]*>[\\s\\S]*?</" + tag + ">";
        var matcher = Pattern.compile(expression).matcher(html);
        List<String> elements = new ArrayList<>();
        while (matcher.find()) elements.add(matcher.group());
        return elements;
    }
}
