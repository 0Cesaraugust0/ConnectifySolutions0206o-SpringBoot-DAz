package com.connectify.controller;

import com.connectify.entity.ClientFavorite;
import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.entity.UserAccount;
import com.connectify.repository.ClientFavoriteRepository;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.EventDetailViewModel;
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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final EventDetailViewModel eventDetailViewModel;
    private final EventPresentationSettingsRepository presentationSettingsRepository;
    private final UserAccountRepository userAccountRepository;
    private final ClientFavoriteRepository favoriteRepository;

    public EventController(EventService eventService,
                           EventDetailViewModel eventDetailViewModel,
                           EventPresentationSettingsRepository presentationSettingsRepository,
                           UserAccountRepository userAccountRepository,
                           ClientFavoriteRepository favoriteRepository) {
        this.eventService = eventService;
        this.eventDetailViewModel = eventDetailViewModel;
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
        model.addAttribute("musicSelected", Category.isMusicName(category));
        model.addAttribute("dateRange", dateRange);
        boolean musicLanding = category != null && "Música".equalsIgnoreCase(category.trim());
        boolean filtered = StringUtils.hasText(q) || StringUtils.hasText(city)
                || StringUtils.hasText(dateRange) || (StringUtils.hasText(category) && !musicLanding);
        model.addAttribute("seoRobots", filtered ? "noindex, follow" : "index, follow, max-image-preview:large");
        model.addAttribute("seoCanonical", "https://www.showyouplay.com/events"
                + (musicLanding ? "?category=M%C3%BAsica" : ""));
        model.addAttribute("seoTitle", musicLanding ? "Conciertos y eventos de música en Perú | Show You Play"
                : "Eventos en Perú y entradas en línea | Show You Play");
        model.addAttribute("seoDescription", musicLanding
                ? "Explora conciertos, artistas cover y presentaciones musicales en Perú. Consulta fechas, lugares y entradas de los eventos publicados en Show You Play."
                : "Descubre conciertos, ferias, actividades culturales y otros eventos en Perú. Compara opciones y compra tus entradas en línea en Show You Play.");
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
        eventDetailViewModel.addAttributes(event, presentation, model);
        var tour = eventService.publishedTourRoute(event, presentation, presentationSettingsRepository);
        model.addAttribute("tourDates", tour.nextDates());
        model.addAttribute("tourOrigin", tour.origin());
        UserAccount client = addAccountNavigation(model, authentication);
        model.addAttribute("favoriteSelected", client != null && favoriteRepository.existsByClient_IdAndEvent_Id(client.getId(), id));
        return "events/marketplace-purchase";
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

}
