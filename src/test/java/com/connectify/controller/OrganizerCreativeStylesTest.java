package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.Category;
import com.connectify.entity.EventStatus;
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
import com.connectify.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrganizerDashboardController.class, properties = {
        "spring.config.location=optional:classpath:/admin-preview-mvc.properties",
        "spring.config.import="
})
@ContextConfiguration(classes = {OrganizerDashboardController.class, OrganizerEventCreationController.class,
        GateSecurityConfig.class, SecurityConfig.class})
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
    @MockBean private ImageStorageService imageStorage;

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
    void musicSpecialtiesAreGroupedAndTheSavedChoiceIsRetainedWhenEditing() throws Exception {
        Category music = category(1L, "Música");
        Category cover = category(8L, "Artistas Cover");
        Category yoSoy = category(9L, "Artistas Yo Soy");
        when(categories.findAll()).thenReturn(List.of(music, category(4L, "Deporte"), cover, yoSoy));
        when(categories.findById(8L)).thenReturn(Optional.of(cover));
        demo.setStatus(EventStatus.DRAFT);

        String form = mvc.perform(get("/dashboard/organizer/events/new")
                        .with(user("org@connectify.com").roles("ORGANIZER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(form).containsPattern("(?s)<optgroup label=\"Música\">.*Música general.*Artistas Cover.*Artistas Yo Soy.*</optgroup>");

        mvc.perform(post("/dashboard/organizer/events/24/edit-all")
                        .with(user("org@connectify.com").roles("ORGANIZER")).with(csrf())
                        .param("title", "Tributo").param("description", "Presentación cover")
                        .param("categoryId", "8").param("eventDate", "2026-12-15T20:00")
                        .param("location", "Auditorio").param("city", "Lima")
                        .param("price", "40").param("capacity", "100"))
                .andExpect(status().is3xxRedirection());
        verify(events).save(demo);
        assertThat(demo.getCategory()).isSameAs(cover);

        String edit = mvc.perform(get("/dashboard/organizer/events/24/edit")
                        .with(user("org@connectify.com").roles("ORGANIZER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(edit).containsPattern("<option(?=[^>]*value=\"8\")(?=[^>]*selected)[^>]*>Artistas Cover</option>");
    }

    private static Category category(long id, String name) {
        Category category = new Category(name, "", "");
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @ParameterizedTest
    @EnumSource(value = EventDesignTemplate.class, names = {"ATELIER", "FESTIVAL", "FORUM", "TICKET_PANEL"})
    void compositionsCanBeComparedSavedAndReopened(EventDesignTemplate style) throws Exception {
        demo.setStatus(EventStatus.DRAFT);
        demo.setCategory(category(1L, "Música"));
        demo.setEventDate(java.time.LocalDateTime.of(2027, 3, 13, 19, 30));
        demo.setLocation("Espacio de demostración");
        demo.setCity("Lima");
        demo.setCapacity(400);
        demo.setPrice(new java.math.BigDecimal("45.00"));
        presentation.setHighlightText("Un encuentro para compartir ideas, música y experiencias.");
        presentation.setShowAgenda(true);
        presentation.setAgendaText("19:30 · Apertura del recinto\n20:00 · Presentación principal\n22:00 · Encuentro y cierre");
        com.connectify.entity.TicketType general = new com.connectify.entity.TicketType();
        general.setEvent(demo);
        general.setName("General");
        general.setDescription("Acceso a la experiencia y al programa principal.");
        general.setPrice(new java.math.BigDecimal("45.00"));
        general.setQuantityAvailable(200);
        when(tickets.findByEventIdOrderByPriceAsc(24L)).thenReturn(List.of(general));
        when(categories.findAll()).thenReturn(List.of(demo.getCategory()));
        when(categories.findById(1L)).thenReturn(Optional.of(demo.getCategory()));

        var owner = user("org@connectify.com").roles("ORGANIZER");
        String preview = mvc.perform(get(GALLERY + "/24").param("template", style.name())
                        .with(user("otro@example.test").roles("ORGANIZER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(preview).contains("data-template=\"" + style.name() + "\"", "event-art-directions.css", "Programa del evento", "Tipos de entrada", "General")
                .doesNotContain("Agregar al carrito");
        assertThat(demo.getDesignTemplate()).isEqualTo(EventDesignTemplate.IMMERSIVE);

        mvc.perform(post("/dashboard/organizer/events/24/edit-all").with(owner).with(csrf())
                        .param("title", demo.getTitle()).param("description", demo.getDescription())
                        .param("categoryId", "1").param("eventDate", "2027-03-13T19:30")
                        .param("location", "Espacio de demostración").param("city", "Lima")
                        .param("price", "45").param("capacity", "400").param("designTemplate", style.name()))
                .andExpect(status().is3xxRedirection());
        verify(events).save(demo);
        assertThat(demo.getDesignTemplate()).isEqualTo(style);
        String edit = mvc.perform(get("/dashboard/organizer/events/24/edit").with(owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(edit).containsPattern("<input(?=[^>]*name=\"designTemplate\")(?=[^>]*value=\"" + style.name() + "\")[^>]*>");
        assertThat(edit).contains("data-template=\"ATELIER\"", "data-template=\"FESTIVAL\"", "data-template=\"FORUM\"", "data-template=\"TICKET_PANEL\"");
        saveVisualProof(style.name() + ".html", preview);
        if (style == EventDesignTemplate.ATELIER) {
            String gallery = mvc.perform(get(GALLERY).with(owner)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(gallery).contains("Atelier", "Pulso", "Foro", "Venta con panel lateral");
            saveVisualProof("gallery.html", gallery);
            saveVisualProof("editor.html", edit);
        }
    }

    private static void saveVisualProof(String name, String html) throws java.io.IOException {
        String directory = System.getProperty("connectify.visualProofDir");
        if (directory == null) return;
        java.nio.file.Path path = java.nio.file.Path.of(directory);
        java.nio.file.Files.createDirectories(path);
        java.nio.file.Files.writeString(path.resolve(name), html);
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
