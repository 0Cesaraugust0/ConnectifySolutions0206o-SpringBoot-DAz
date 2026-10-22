package com.connectify.controller;

import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventDesignTemplate;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.entity.Role;
import com.connectify.entity.TicketType;
import com.connectify.entity.UserAccount;
import com.connectify.repository.ClientFavoriteRepository;
import com.connectify.repository.EventAdminRecordRepository;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.security.DatabaseUserDetailsService;
import com.connectify.security.GateSecurityConfig;
import com.connectify.security.SecurityConfig;
import com.connectify.service.EventDetailViewModel;
import com.connectify.service.EventGateAccessService;
import com.connectify.service.EventService;
import com.connectify.service.InternalMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Real templates, shared presentation model and administrator security; all persistence uses fixtures. */
@WebMvcTest(controllers = {EventController.class, AdminDashboardController.class}, properties = {
        "spring.config.location=optional:classpath:/admin-preview-mvc.properties",
        "spring.config.import="
})
@ContextConfiguration(classes = {EventController.class, AdminDashboardController.class,
        EventDetailViewModel.class, GateSecurityConfig.class, SecurityConfig.class})
@ActiveProfiles("prod")
class AdminMarketplacePreviewTemplateTest {
    private static final long EVENT_ID = 12L;
    private static final String CLIENT_EMAIL = "fixture-client@example.test";
    private static final String PREVIEW_URL = "/dashboard/admin/events/12/preview";
    private static final String COVER = "/images/showcase/pulso-lima-cover.png";
    private static final String THUMBNAIL = "/images/showcase/origen-cover.png";
    private static final String BACKGROUND = "/images/showcase/nexum-cover.png";
    private static final String STAGE = "/images/showcase/pulso-lima-stage.svg";
    private static final String LOGO = "/images/showcase/organizer-mark.svg";

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockBean private EventService events;
    @MockBean private EventRepository eventRepository;
    @MockBean private TicketTypeRepository tickets;
    @MockBean private EventPresentationSettingsRepository presentations;
    @MockBean private UserAccountRepository accounts;
    @MockBean private ClientFavoriteRepository favorites;
    @MockBean private EventAdminRecordRepository records;
    @MockBean private InternalMessageService messages;
    @MockBean private EventGateAccessService gateAccess;
    @MockBean private DatabaseUserDetailsService users;

    private Event event;
    private EventPresentationSettings presentation;

    @BeforeEach
    void fixtures() {
        event = new Event();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.setTitle("Festival Pulso Lima");
        event.setDescription("Experiencia musical junto al mar.");
        event.setCategory(new Category("Música", "", "Conciertos y festivales"));
        event.setEventDate(LocalDateTime.of(2026, 11, 21, 18, 30));
        event.setLocation("Parque de la Exposición");
        event.setCity("Lima");
        event.setOrganizerName("Organizador original");
        event.setOrganizerEmail("fixture-organizer@example.test");
        event.setPrice(new BigDecimal("55.00"));
        event.setCapacity(600);
        event.setSold(80);
        event.setImageUrl(THUMBNAIL);
        event.setDesignTemplate(EventDesignTemplate.STAGE_MEDIA);
        event.setStatus(EventStatus.PUBLISHED);

        presentation = new EventPresentationSettings();
        presentation.setEvent(event);
        presentation.setCoverImageUrl(COVER);
        presentation.setThumbnailImageUrl(THUMBNAIL);
        presentation.setBackgroundImageUrl(BACKGROUND);
        presentation.setStageImageUrl(STAGE);
        presentation.setOrganizerBrandName("Pulso Producciones");
        presentation.setOrganizerLogoUrl(LOGO);
        presentation.setPrimaryColor("#245463");
        presentation.setAccentColor("#db7852");
        presentation.setCoverFocus("TOP");
        presentation.setHighlightText("Una noche para encontrarnos con la música.");
        presentation.setShowGallery(true);
        presentation.setShowOrganizer(true);
        presentation.setShowStageMap(true);
        presentation.setStageText("Acceso norte y zonas General y Preferente.");
        presentation.setShowMedia(true);
        presentation.setYoutubeUrl("https://youtu.be/fixtureVideo12?si=fixture");
        presentation.setSpotifyUrl("https://open.spotify.com/playlist/fixturePlaylist12?si=fixture");
        presentation.setShowSessions(true);
        presentation.setSessionText("Lima · 21 de noviembre\nArequipa · 28 de noviembre");
        presentation.setShowAgenda(true);
        presentation.setAgendaText("18:30 Apertura de puertas\n20:00 Concierto principal");
        presentation.setShowBenefits(true);
        presentation.setBenefitsText("Acceso a todas las presentaciones\nZona gastronómica");
        presentation.setShowSponsors(true);
        presentation.setSponsorsText("Con el apoyo de Onda y Raíz");
        presentation.setSponsorLogosText("/images/showcase/partner-onda.svg\n/images/showcase/partner-raiz.svg");

        TicketType general = ticket(1201L, "General", "Ingreso a la zona general.", "55.00", 500, 80);
        TicketType preferente = ticket(1202L, "Preferente", "Acceso a zona preferente y lounge.", "95.00", 100, 10);
        when(events.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(presentations.findByEventId(EVENT_ID)).thenReturn(Optional.of(presentation));
        when(tickets.findByEventIdAndActiveTrueOrderByPriceAsc(EVENT_ID)).thenReturn(List.of(general, preferente));

        UserAccount client = new UserAccount("Lucía Fixture", CLIENT_EMAIL, "fixture-only", Role.CLIENT);
        ReflectionTestUtils.setField(client, "id", 120L);
        when(accounts.findByEmailIgnoreCase(CLIENT_EMAIL)).thenReturn(Optional.of(client));
    }

    @Test
    void artistChipsAreStableOutsideTheCardsAndPreserveTheOtherFilters() throws Exception {
        when(events.findPublished("tributo", "Lima", "Artistas Cover", "week")).thenReturn(List.of());
        String html = mvc.perform(get("/events").param("q", "tributo").param("city", "Lima")
                        .param("category", "Artistas Cover").param("dateRange", "week"))
                .andExpect(status().isOk()).andExpect(model().attribute("musicSelected", true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Artistas Cover", "Artistas Yo Soy", "aria-current=\"true\"",
                "q=tributo&amp;city=Lima&amp;category=Artistas%20Yo%20Soy&amp;dateRange=week");
        assertThat(html.indexOf("marketplace-music-types")).isLessThan(html.indexOf("id=\"marketplaceCards\""));
        assertThat(html).contains("No encontramos eventos con esos filtros");
    }

    @Test
    void previewRendersTheSameMarketplacePresentationWithoutPurchaseControls() throws Exception {
        MvcResult marketplace = mvc.perform(get("/events/12").with(user(CLIENT_EMAIL).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(view().name("events/marketplace-purchase"))
                .andReturn();
        MvcResult preview = mvc.perform(get(PREVIEW_URL).with(user("fixture-admin").roles("ADMIN", "CLIENT")))
                .andExpect(status().isOk()).andExpect(view().name("events/marketplace-purchase"))
                .andExpect(model().attribute("adminPreview", true))
                .andExpect(model().attribute("adminReturnUrl", "/dashboard/admin/events/12"))
                .andReturn();

        Map<String, Object> publicModel = marketplace.getModelAndView().getModel();
        Map<String, Object> previewModel = preview.getModelAndView().getModel();
        for (String attribute : List.of("event", "ticketTypes", "presentation", "coverImageUrl", "thumbnailImageUrl",
                "backgroundImageUrl", "stageImageUrl", "organizerBrandName", "organizerLogoUrl",
                "youtubeEmbedUrl", "spotifyEmbedUrl", "presentationStyle")) {
            assertThat(previewModel.get(attribute)).as("shared marketplace attribute %s", attribute)
                    .isEqualTo(publicModel.get(attribute));
        }
        assertThat(publicModel).containsEntry("coverImageUrl", COVER)
                .containsEntry("thumbnailImageUrl", THUMBNAIL)
                .containsEntry("backgroundImageUrl", BACKGROUND)
                .containsEntry("stageImageUrl", STAGE)
                .containsEntry("organizerLogoUrl", LOGO)
                .containsEntry("youtubeEmbedUrl", "https://www.youtube.com/embed/fixtureVideo12")
                .containsEntry("spotifyEmbedUrl", "https://open.spotify.com/embed/playlist/fixturePlaylist12?utm_source=generator");

        String publicHtml = html(marketplace);
        String previewHtml = html(preview);
        for (String rendered : List.of(publicHtml, previewHtml)) {
            assertThat(rendered).contains("Festival Pulso Lima", "Experiencia musical junto al mar.",
                    "Una noche para encontrarnos con la música.", "21/11/2026 18:30", "Parque de la Exposición",
                    "Pulso Producciones", "Lima · 21 de noviembre", "Arequipa · 28 de noviembre",
                    "18:30 Apertura de puertas", "20:00 Concierto principal", "Zona gastronómica",
                    "Con el apoyo de Onda y Raíz", "Acceso norte y zonas General y Preferente.",
                    "Ingreso a la zona general.", "Acceso a zona preferente y lounge.", "S/ 55.00", "S/ 95.00");
            for (String source : List.of(COVER, THUMBNAIL, STAGE, LOGO,
                    "/images/showcase/partner-onda.svg", "/images/showcase/partner-raiz.svg")) {
                assertThat(sources(rendered, "img")).contains(source);
            }
            assertThat(openingTagWithClass(rendered, "section", "market-layout"))
                    .contains("has-background", BACKGROUND, "data-template=\"STAGE_MEDIA\"");
            assertThat(elements(rendered, "tr")).anySatisfy(row -> assertThat(row)
                    .contains("General", "S/ 55.00", ">420</td>"))
                    .anySatisfy(row -> assertThat(row).contains("Preferente", "S/ 95.00", ">90</td>"));
        }
        assertThat(sources(previewHtml, "iframe")).containsExactlyElementsOf(sources(publicHtml, "iframe"))
                .containsExactly("https://www.youtube.com/embed/fixtureVideo12",
                        "https://open.spotify.com/embed/playlist/fixturePlaylist12?utm_source=generator");
        assertThat(attribute(openingTagWithClass(previewHtml, "section", "market-layout"), "style"))
                .isEqualTo(attribute(openingTagWithClass(publicHtml, "section", "market-layout"), "style"));
        assertThat(publicHtml).doesNotContain("action=\"/cart/add/12\"", "Agregar al carrito")
                .contains("Solo demostración");
        assertReviewOnlyControls(previewHtml);
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();

        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/admin-marketplace-preview.html"), previewHtml, StandardCharsets.UTF_8);
    }

    @Test
    void draftRemainsVisibleOnlyInTheAuthorizedAdministratorPreview() throws Exception {
        event.setStatus(EventStatus.DRAFT);

        String previewHtml = html(mvc.perform(get(PREVIEW_URL).with(user("fixture-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("events/marketplace-purchase"))
                .andExpect(model().attribute("event", event)).andExpect(model().attribute("adminPreview", true))
                .andReturn());
        assertThat(previewHtml).contains("Festival Pulso Lima", "Ingreso a la zona general.");

        mvc.perform(get("/events/12")).andExpect(status().isNotFound());
        verify(presentations).findByEventId(EVENT_ID);
        verify(tickets).findByEventIdAndActiveTrueOrderByPriceAsc(EVENT_ID);
        verifyNoInteractions(accounts, favorites, messages, gateAccess, records);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void otherAccountRolesCannotReadTheAdministratorPreview(Role role) throws Exception {
        mvc.perform(get(PREVIEW_URL).with(user("fixture-other-role").roles(role.name())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventRepository, events, presentations, tickets, accounts, favorites, messages, gateAccess, records);
    }

    @Test
    void anonymousVisitorMustSignInBeforeThePreviewIsRead() throws Exception {
        mvc.perform(get(PREVIEW_URL)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(eventRepository, events, presentations, tickets, accounts, favorites, messages, gateAccess, records);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scenarioControlRequiresBothAnEnabledMapAndAnImage(boolean imageMissing) throws Exception {
        if (imageMissing) presentation.setStageImageUrl(" ");
        else presentation.setShowStageMap(false);

        String rendered = html(mvc.perform(get(PREVIEW_URL).with(user("fixture-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn());
        assertThat(rendered).doesNotContain("stageMapDialog", "Ver escenario");
        assertThat(elements(rendered, "button")).isEmpty();
        assertThat(elements(rendered, "form")).isEmpty();
    }

    @Test
    void missingPresentationUsesTheSameEventImageFallbackInBothRoutes() throws Exception {
        when(presentations.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        MvcResult marketplace = mvc.perform(get("/events/12")).andExpect(status().isOk()).andReturn();
        MvcResult preview = mvc.perform(get(PREVIEW_URL).with(user("fixture-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(model().attribute("coverImageUrl", THUMBNAIL))
                .andExpect(model().attribute("thumbnailImageUrl", THUMBNAIL))
                .andExpect(model().attribute("backgroundImageUrl", ""))
                .andExpect(model().attribute("stageImageUrl", ""))
                .andExpect(model().attribute("organizerBrandName", "Organizador original"))
                .andReturn();

        assertThat(sources(html(preview), "img")).contains(THUMBNAIL)
                .containsExactlyElementsOf(sources(html(marketplace), "img"));
        assertThat(html(preview)).contains("Organizador original")
                .doesNotContain("stageMapDialog", "market-media-embed", "market-gallery-section");
    }

    @Test
    void unsupportedMediaLinksRemainPublicOnlyAndCannotAddActionsToTheReviewBody() throws Exception {
        presentation.setYoutubeUrl("https://example.test/event-video");
        presentation.setSpotifyUrl("https://example.test/event-playlist");
        String publicHtml = html(mvc.perform(get("/events/12")).andExpect(status().isOk()).andReturn());
        String previewHtml = html(mvc.perform(get(PREVIEW_URL).with(user("fixture-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn());

        assertThat(publicHtml).contains("href=\"https://example.test/event-video\"",
                "href=\"https://example.test/event-playlist\"");
        assertThat(sources(previewHtml, "iframe")).isEmpty();
        assertReviewOnlyControls(previewHtml);
    }

    private TicketType ticket(long id, String name, String description, String price, int available, int sold) {
        TicketType ticket = new TicketType();
        ReflectionTestUtils.setField(ticket, "id", id);
        ticket.setEvent(event);
        ticket.setName(name);
        ticket.setDescription(description);
        ticket.setPrice(new BigDecimal(price));
        ticket.setQuantityAvailable(available);
        ticket.setQuantitySold(sold);
        ticket.setActive(true);
        return ticket;
    }

    private static void assertReviewOnlyControls(String html) {
        assertThat(openingTagWithClass(html, "body", "admin-event-preview")).isNotBlank();
        assertThat(openingTagWithClass(html, "main", "admin-preview-page")).isNotBlank();
        String header = elements(html, "header").stream()
                .filter(element -> hasClass(element, "admin-preview-nav")).findFirst().orElseThrow();
        assertThat(elements(header, "a")).anySatisfy(link -> assertThat(link)
                        .contains("href=\"/dashboard/admin/events?view=list\"", "Eventos"))
                .anySatisfy(link -> assertThat(link).contains("href=\"/dashboard/admin/events/12\"", "Volver a revisión"));
        assertThat(elements(header, "button")).singleElement()
                .satisfies(button -> assertThat(button).contains("data-stage-modal-open=\"stageMapDialog\"", "Ver escenario"));
        assertThat(elements(html, "form")).isEmpty();
        assertThat(html).contains("Vista de revisión").doesNotContain("Agregar al carrito", "Ver mi carrito",
                "Comprar entrada", "Solo cuenta Cliente", "Iniciar sesión", "Crear cuenta", "Seguir explorando",
                "Administrar evento", "Guardados", "market-account-cluster");
        String outsideHeader = html.replace(header, "");
        assertThat(elements(outsideHeader, "a")).isEmpty();
        assertThat(elements(outsideHeader, "button")).singleElement()
                .satisfies(button -> assertThat(button).contains("data-stage-modal-close", "Cerrar figura del escenario"));
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> sources(String html, String tag) {
        var matcher = Pattern.compile("<" + tag + "\\b[^>]*>").matcher(html);
        List<String> sources = new ArrayList<>();
        while (matcher.find()) {
            String source = attribute(matcher.group(), "src");
            if (source != null) sources.add(source);
        }
        return sources;
    }

    private static String openingTagWithClass(String html, String tag, String cssClass) {
        var matcher = Pattern.compile("<" + tag + "\\b[^>]*>").matcher(html);
        while (matcher.find()) if (hasClass(matcher.group(), cssClass)) return matcher.group();
        throw new AssertionError("Missing " + tag + "." + cssClass);
    }

    private static boolean hasClass(String element, String cssClass) {
        String classes = attribute(element, "class");
        return classes != null && List.of(classes.split("\\s+")).contains(cssClass);
    }

    private static String attribute(String element, String name) {
        var matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"")
                .matcher(element.substring(0, element.indexOf('>') + 1));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> elements(String html, String tag) {
        var matcher = Pattern.compile("<" + tag + "\\b[^>]*>[\\s\\S]*?</" + tag + ">").matcher(html);
        List<String> elements = new ArrayList<>();
        while (matcher.find()) elements.add(matcher.group());
        return elements;
    }
}
