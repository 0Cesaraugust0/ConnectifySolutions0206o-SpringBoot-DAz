package com.connectify.controller;

import com.connectify.entity.ClientFavorite;
import com.connectify.entity.Event;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.entity.UserAccount;
import com.connectify.repository.ClientFavoriteRepository;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventPresentationSettingsRepository presentationSettingsRepository;
    private final UserAccountRepository userAccountRepository;
    private final ClientFavoriteRepository favoriteRepository;

    public EventController(EventService eventService,
                           TicketTypeRepository ticketTypeRepository,
                           EventPresentationSettingsRepository presentationSettingsRepository,
                           UserAccountRepository userAccountRepository,
                           ClientFavoriteRepository favoriteRepository) {
        this.eventService = eventService;
        this.ticketTypeRepository = ticketTypeRepository;
        this.presentationSettingsRepository = presentationSettingsRepository;
        this.userAccountRepository = userAccountRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String city,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String dateRange,
                       Model model,
                       Authentication authentication) {
        List<Event> events = eventService.findPublished(q, city, category, dateRange);
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, EventPresentationSettings> presentationByEventId = eventIds.isEmpty()
                ? Map.of()
                : presentationSettingsRepository.findByEvent_IdIn(eventIds).stream()
                .filter(setting -> setting.getEvent() != null && setting.getEvent().getId() != null)
                .collect(Collectors.toMap(setting -> setting.getEvent().getId(), Function.identity(), (first, ignored) -> first));

        UserAccount client = addAccountNavigation(model, authentication);
        model.addAttribute("favoriteEventIds", favoriteIds(client));
        model.addAttribute("events", events);
        model.addAttribute("presentationByEventId", presentationByEventId);
        model.addAttribute("q", q);
        model.addAttribute("city", city);
        model.addAttribute("category", category);
        model.addAttribute("dateRange", dateRange);
        return "events/marketplace-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, Authentication authentication) {
        Event event = eventService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no disponible en marketplace");
        }

        EventPresentationSettings presentation = presentationSettingsRepository.findByEventId(id).orElse(null);
        addDetailModel(event, presentation, model);
        UserAccount client = addAccountNavigation(model, authentication);
        model.addAttribute("favoriteSelected", client != null && favoriteRepository.existsByClient_IdAndEvent_Id(client.getId(), id));
        return "events/marketplace-purchase";
    }

    private void addDetailModel(Event event, EventPresentationSettings presentation, Model model) {
        model.addAttribute("event", event);
        model.addAttribute("ticketTypes", ticketTypeRepository.findByEventIdAndActiveTrueOrderByPriceAsc(event.getId()));
        model.addAttribute("presentation", presentation);
        model.addAttribute("coverImageUrl", firstText(presentation == null ? null : presentation.getCoverImageUrl(), event.getImageUrl()));
        model.addAttribute("thumbnailImageUrl", firstText(presentation == null ? null : presentation.getThumbnailImageUrl(), event.getImageUrl()));
        model.addAttribute("backgroundImageUrl", firstText(presentation == null ? null : presentation.getBackgroundImageUrl(), ""));
        model.addAttribute("stageImageUrl", firstText(presentation == null ? null : presentation.getStageImageUrl(), ""));
        model.addAttribute("organizerBrandName", firstText(presentation == null ? null : presentation.getOrganizerBrandName(), event.getOrganizerName()));
        model.addAttribute("organizerLogoUrl", firstText(presentation == null ? null : presentation.getOrganizerLogoUrl(), ""));
        model.addAttribute("youtubeEmbedUrl", embedYoutube(presentation == null ? null : presentation.getYoutubeUrl()));
        model.addAttribute("spotifyEmbedUrl", embedSpotify(presentation == null ? null : presentation.getSpotifyUrl()));
        model.addAttribute("presentationStyle", styleFor(presentation));
    }

    private UserAccount addAccountNavigation(Model model, Authentication authentication) {
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
        boolean clientAccount = signedIn && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CLIENT".equals(authority.getAuthority()));

        UserAccount account = null;
        if (clientAccount) {
            account = userAccountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        }

        model.addAttribute("signedIn", signedIn);
        model.addAttribute("clientAccount", clientAccount && account != null);
        model.addAttribute("accountEmail", signedIn ? authentication.getName() : "");
        model.addAttribute("accountFirstName", account == null ? "Mi cuenta" : firstName(account.getFullName()));
        model.addAttribute("favoriteCount", account == null ? 0L : favoriteRepository.countByClient_Id(account.getId()));
        return account;
    }

    private Set<Long> favoriteIds(UserAccount account) {
        if (account == null) return Set.of();
        return favoriteRepository.findByClient_EmailIgnoreCaseOrderByCreatedAtDesc(account.getEmail()).stream()
                .map(ClientFavorite::getEvent)
                .filter(event -> event != null && event.getId() != null)
                .map(Event::getId)
                .collect(Collectors.toSet());
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Mi cuenta";
        return fullName.trim().split("\\s+")[0];
    }

    private String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback == null ? "" : fallback;
    }

    private String styleFor(EventPresentationSettings presentation) {
        if (presentation == null) return "";
        String background = safeBackground(presentation.getBackgroundImageUrl());
        return "--market-primary:" + safeColor(presentation.getPrimaryColor(), "#4338ca") + ";"
                + "--market-accent:" + safeColor(presentation.getAccentColor(), "#7c3aed") + ";"
                + "--market-cover-position:" + coverPosition(presentation.getCoverFocus()) + ";"
                + (background.isBlank() ? "" : "--market-background-image:url('" + background + "');");
    }

    private String safeColor(String color, String fallback) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : fallback;
    }

    private String coverPosition(String value) {
        if ("TOP".equals(value)) return "50% 18%";
        if ("BOTTOM".equals(value)) return "50% 82%";
        return "50% 50%";
    }

    private String safeBackground(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        return normalized.startsWith("https://") || normalized.startsWith("http://") || normalized.startsWith("/uploads/")
                || normalized.startsWith("/images/showcase/")
                ? normalized.replace("'", "%27") : "";
    }

    private String embedSpotify(String value) {
        if (value == null || value.isBlank()) return "";
        String url = value.trim();
        if (!url.startsWith("https://open.spotify.com/")) return "";
        String clean = url.split("\\?")[0].replace("https://open.spotify.com/", "https://open.spotify.com/embed/");
        return clean + "?utm_source=generator";
    }

    private String embedYoutube(String value) {
        if (value == null || value.isBlank()) return "";
        String url = value.trim();
        String id = "";
        int watchIndex = url.indexOf("watch?v=");
        if (watchIndex >= 0) {
            id = url.substring(watchIndex + 8).split("&")[0];
        } else if (url.startsWith("https://youtu.be/")) {
            id = url.substring("https://youtu.be/".length()).split("\\?")[0];
        } else if (url.startsWith("https://www.youtube.com/embed/")) {
            id = url.substring("https://www.youtube.com/embed/".length()).split("\\?")[0];
        }
        return id.isBlank() ? "" : "https://www.youtube.com/embed/" + id;
    }
}
