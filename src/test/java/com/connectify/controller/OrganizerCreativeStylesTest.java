package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.EventDesignTemplate;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.repository.CategoryRepository;
import com.connectify.repository.EventAdminRecordRepository;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.security.DatabaseUserDetailsService;
import com.connectify.security.GateSecurityConfig;
import com.connectify.security.SecurityConfig;
import com.connectify.service.EventGateAccessService;
import com.connectify.service.InternalMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrganizerDashboardController.class, properties = {
        "spring.config.location=optional:classpath:/admin-preview-mvc.properties",
        "spring.config.import="
})
@ContextConfiguration(classes = {OrganizerDashboardController.class, GateSecurityConfig.class, SecurityConfig.class})
@ActiveProfiles("prod")
class OrganizerCreativeStylesTest {
    private static final String GALLERY = "/dashboard/organizer/events/creative-styles";

    @Autowired private MockMvc mvc;
    @MockBean private EventRepository events;
    @MockBean private CategoryRepository categories;
    @MockBean private TicketTypeRepository tickets;
    @MockBean private EventAdminRecordRepository records;
    @MockBean private EventPresentationSettingsRepository presentations;
    @MockBean private InternalMessageService messages;
    @MockBean private UserAccountRepository accounts;
    @MockBean private EventGateAccessService gateAccess;
    @MockBean private DatabaseUserDetailsService users;

    private Event demo;
    private EventPresentationSettings presentation;

    @BeforeEach
    void fixture() {
        demo = new Event();
        ReflectionTestUtils.setField(demo, "id", 24L);
        demo.setTitle("ORIGEN · Mesa del Pacífico");
        demo.setDescription("Propuesta ficticia de demostración.");
        demo.setOrganizerEmail("org@connectify.com");
        demo.setDesignTemplate(EventDesignTemplate.IMMERSIVE);
        presentation = new EventPresentationSettings();
        presentation.setEvent(demo);
        presentation.setCoverImageUrl("/images/showcase/origen-cover.png");
        when(events.findByOrganizerEmailIgnoreCaseOrderByUpdatedAtDesc("org@connectify.com"))
                .thenReturn(List.of(demo));
        when(events.findById(24L)).thenReturn(Optional.of(demo));
        when(presentations.findByEvent_IdIn(List.of(24L))).thenReturn(List.of(presentation));
        when(presentations.findByEventId(24L)).thenReturn(Optional.of(presentation));
        when(tickets.findByEventIdOrderByPriceAsc(24L)).thenReturn(List.of());
    }

    @Test
    void anyOrganizerCanBrowseExamplesAndCompareReadOnlyPreview() throws Exception {
        var organizer = user("nuevo@example.test").roles("ORGANIZER");
        String newEvent = mvc.perform(get("/dashboard/organizer/events/new").with(organizer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(newEvent).contains("Estilos creativos", GALLERY);

        String gallery = mvc.perform(get(GALLERY).with(organizer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(gallery).contains("Estilos creativos", "ORIGEN", "composiciones",
                "/dashboard/organizer/events/creative-styles/24?template=CLASSIC",
                "/dashboard/organizer/events/creative-styles/24?template=CARD_GRID")
                .doesNotContain("Agregar al carrito");

        String preview = mvc.perform(get(GALLERY + "/24").with(organizer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("Volver a la sala", "Suceso demostrativo")
                .doesNotContain("Gestionar entradas", "Agregar al carrito", "Administrar evento");
    }

    @Test
    void galleryCannotExposeAnotherOrganizersEventOrClientAccess() throws Exception {
        demo.setOrganizerEmail("organizador-real@example.test");
        mvc.perform(get(GALLERY + "/24").with(user("nuevo@example.test").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
        mvc.perform(get(GALLERY).with(user("cliente@example.test").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }
}
