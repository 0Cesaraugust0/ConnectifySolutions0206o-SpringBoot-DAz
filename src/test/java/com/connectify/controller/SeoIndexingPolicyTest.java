package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.repository.*;
import com.connectify.security.CanonicalHostFilter;
import com.connectify.security.PrivateAreaNoIndexFilter;
import com.connectify.service.EventDetailViewModel;
import com.connectify.service.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeoIndexingPolicyTest {
    private final EventDetailViewModel detail = new EventDetailViewModel(mock(TicketTypeRepository.class));
    private final EventController controller = new EventController(mock(EventService.class), detail,
            mock(EventPresentationSettingsRepository.class), mock(UserAccountRepository.class),
            mock(ClientFavoriteRepository.class));

    @Test
    void musicHasItsOwnCanonicalAndSearchCombinationsAreNoindex() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        controller.list(null, null, "Música", null, model, null);
        String html = head("events/marketplace-list", model);
        assertThat(html).contains("Conciertos y eventos de música en Perú", "index, follow, max-image-preview:large",
                "https://www.showyouplay.com/events?category=M%C3%BAsica");

        controller.list("rock", "Lima", "Música", "today", model, null);
        assertThat(head("events/marketplace-list", model)).contains("noindex, follow");
        controller.list("", "", "", "", model, null);
        assertThat(head("events/marketplace-list", model)).contains("index, follow, max-image-preview:large")
                .doesNotContain("?category=");
    }

    @Test
    void realDetailIsIndexableButDemoAndRolePreviewsAreNot() throws Exception {
        Event event = new Event();
        event.setTitle("Concierto real");
        event.setOrganizerEmail("organizador@example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", 40L);
        ExtendedModelMap model = new ExtendedModelMap();
        detail.addAttributes(event, null, model);
        assertThat(head("events/marketplace-purchase", model)).contains("index, follow, max-image-preview:large");
        event.setOrganizerEmail("org@connectify.com");
        detail.addAttributes(event, null, model);
        assertThat(head("events/marketplace-purchase", model)).contains("noindex, follow");
        event.setOrganizerEmail("organizador@example.test");
        detail.addAttributes(event, null, model);
        model.addAttribute("adminPreview", true);
        assertThat(head("events/marketplace-purchase", model)).contains("noindex, follow");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/dashboard/client", "/cart/checkout", "/communications/1"})
    void privateResponsesRequestNoIndex(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PrivateAreaNoIndexFilter().doFilter(new MockHttpServletRequest("GET", path), response, (req, res) -> {});
        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
    }

    @Test
    void registrationIsPublicAndSupportPagesStayCrawlableWithNoindex() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PrivateAreaNoIndexFilter().doFilter(new MockHttpServletRequest("GET", "/register"), response, (req, res) -> {});
        assertThat(response.getHeader("X-Robots-Tag")).isNull();
        assertThat(head("auth/register", new ExtendedModelMap())).contains("index, follow").doesNotContain("noindex");
        for (String page : new String[]{"help/index", "legal/terms", "legal/privacy", "legal/cookies"}) {
            assertThat(head(page, new ExtendedModelMap())).contains("noindex, follow");
        }
    }

    @Test
    void bareDomainPublicPagesRedirectWithoutChangingQueryOrCallbacks() throws Exception {
        CanonicalHostFilter filter = new CanonicalHostFilter("https://www.showyouplay.com/");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        request.setServerName("showyouplay.com");
        request.setQueryString("category=M%C3%BAsica");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Expected redirect"); });
        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeader("Location")).isEqualTo("https://www.showyouplay.com/events?category=M%C3%BAsica");
        for (String host : new String[]{"localhost", "www.showyouplay.com"}) {
            request = new MockHttpServletRequest("GET", "/events");
            request.setServerName(host);
            AtomicBoolean called = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
            assertThat(called.get()).isTrue();
        }
        request = new MockHttpServletRequest("POST", "/payments/izipay/callback");
        request.setServerName("showyouplay.com");
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called.get()).isTrue();
        assertThat(new HomeController().index().getStatusCode().value()).isEqualTo(301);
        assertThat(new HomeController().index().getHeaders().getLocation().toString()).isEqualTo("/events");
    }

    private String head(String name, ExtendedModelMap model) throws Exception {
        String source = Files.readString(Path.of("src/main/resources/templates/" + name + ".html"));
        source = source.substring(source.indexOf("<head>"), source.indexOf("    <link rel=\"stylesheet\"")) + "</head>";
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        Context context = new Context();
        context.setVariables(model.asMap());
        return engine.process(source, context);
    }
}
