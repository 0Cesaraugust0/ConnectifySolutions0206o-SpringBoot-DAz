package com.connectify.controller;

import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventDesignTemplate;
import com.connectify.entity.EventAdminRecord;
import com.connectify.entity.EventAdminRecordType;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.entity.MessagePriority;
import com.connectify.entity.MessageType;
import com.connectify.entity.Role;
import com.connectify.entity.TicketType;
import com.connectify.entity.UserAccount;
import com.connectify.repository.CategoryRepository;
import com.connectify.repository.EventAdminRecordRepository;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.InternalMessageService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/dashboard/organizer/events")
public class OrganizerDashboardController {

    private static final DateTimeFormatter BITACORA_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", new Locale("es", "PE"));
    private static final String DEMO_ORGANIZER_EMAIL = "org@connectify.com";

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventAdminRecordRepository recordRepository;
    private final EventPresentationSettingsRepository presentationSettingsRepository;
    private final InternalMessageService messageService;
    private final UserAccountRepository userAccountRepository;

    public OrganizerDashboardController(EventRepository eventRepository,
                                        CategoryRepository categoryRepository,
                                        TicketTypeRepository ticketTypeRepository,
                                        EventAdminRecordRepository recordRepository,
                                        EventPresentationSettingsRepository presentationSettingsRepository,
                                        InternalMessageService messageService,
                                        UserAccountRepository userAccountRepository) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.recordRepository = recordRepository;
        this.presentationSettingsRepository = presentationSettingsRepository;
        this.messageService = messageService;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String events(Model model, Authentication authentication) {
        model.addAttribute("events", eventRepository.findByOrganizerEmailIgnoreCaseOrderByUpdatedAtDesc(currentEmail(authentication)));
        return "dashboard/organizer/events";
    }

    @GetMapping("/new")
    public String newEvent(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        return "dashboard/organizer/event-form";
    }

    @GetMapping("/creative-styles")
    public String creativeStyles(Model model) {
        List<Event> ownedEvents = eventRepository.findByOrganizerEmailIgnoreCaseOrderByUpdatedAtDesc(DEMO_ORGANIZER_EMAIL);
        Map<Long, EventPresentationSettings> presentations = new LinkedHashMap<>();
        if (!ownedEvents.isEmpty()) {
            presentationSettingsRepository.findByEvent_IdIn(ownedEvents.stream().map(Event::getId).toList())
                    .forEach(settings -> presentations.put(settings.getEvent().getId(), settings));
        }
        List<Event> examples = ownedEvents.stream().filter(event -> presentations.containsKey(event.getId())).toList();
        Map<Long, String> images = new LinkedHashMap<>();
        Map<String, Long> styleEventIds = new LinkedHashMap<>();
        for (Event event : examples) {
            EventPresentationSettings settings = presentations.get(event.getId());
            images.put(event.getId(), firstText(settings.getThumbnailImageUrl(),
                    firstText(settings.getCoverImageUrl(), event.getImageUrl())));
            if (event.getDesignTemplate() != null) {
                styleEventIds.putIfAbsent(event.getDesignTemplate().name(), event.getId());
            }
        }
        Long fallbackId = examples.isEmpty() ? null : examples.getFirst().getId();
        for (EventDesignTemplate style : EventDesignTemplate.values()) {
            styleEventIds.putIfAbsent(style.name(), fallbackId);
        }
        model.addAttribute("examples", examples);
        model.addAttribute("exampleImages", images);
        model.addAttribute("styles", EventDesignTemplate.values());
        model.addAttribute("styleEventIds", styleEventIds);
        return "dashboard/organizer/creative-styles";
    }

    @GetMapping("/creative-styles/{id}")
    public String creativeStylePreview(@PathVariable Long id,
                                       @RequestParam(required = false) EventDesignTemplate template,
                                       Model model) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        EventPresentationSettings presentation = presentationSettingsRepository.findByEventId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (event.getOrganizerEmail() == null || !DEMO_ORGANIZER_EMAIL.equalsIgnoreCase(event.getOrganizerEmail())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        addMarketplacePreviewModel(event, presentation, model);
        model.addAttribute("previewTemplate", template != null ? template
                : event.getDesignTemplate() != null ? event.getDesignTemplate() : EventDesignTemplate.CLASSIC);
        model.addAttribute("previewTemplates", EventDesignTemplate.values());
        model.addAttribute("organizerPreview", true);
        model.addAttribute("creativeStylesPreview", true);
        model.addAttribute("locked", true);
        model.addAttribute("signedIn", true);
        model.addAttribute("clientAccount", false);
        model.addAttribute("favoriteCount", 0L);
        return "events/marketplace-purchase";
    }

    @PostMapping
    public String createEvent(@RequestParam String title,
                              @RequestParam String description,
                              @RequestParam Long categoryId,
                              @RequestParam String eventDate,
                              @RequestParam String location,
                              @RequestParam String city,
                              @RequestParam BigDecimal price,
                              @RequestParam Integer capacity,
                              @RequestParam(required = false) String imageUrl,
                              Authentication authentication) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        String organizerEmail = currentEmail(authentication);
        Event event = new Event();
        event.setTitle(title);
        event.setDescription(description);
        event.setCategory(category);
        event.setEventDate(LocalDateTime.parse(eventDate));
        event.setLocation(location);
        event.setCity(city);
        event.setOrganizerName(organizerDisplayName(organizerEmail));
        event.setOrganizerEmail(organizerEmail);
        event.setPrice(price);
        event.setCapacity(capacity);
        event.setSold(0);
        event.setImageUrl(imageUrl == null ? "" : imageUrl);
        event.setFeatured(false);
        event.setStatus(EventStatus.PENDING_REVIEW);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        Event saved = eventRepository.save(event);
        createRecord(saved, EventAdminRecordType.CREATED, "Evento creado por organizador y enviado a revisión administrativa.");
        return "redirect:/dashboard/organizer/events/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication authentication) {
        findOwnedEvent(id, authentication);
        return "redirect:/dashboard/organizer/events/" + id + "/edit";
    }

    @GetMapping("/{id}/preview")
    public String preview(@PathVariable Long id,
                          @RequestParam(required = false) EventDesignTemplate template,
                          Model model, Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        EventPresentationSettings presentation = presentationSettingsRepository.findByEventId(id).orElse(null);
        addMarketplacePreviewModel(event, presentation, model);
        model.addAttribute("previewTemplate", template != null ? template
                : event.getDesignTemplate() != null ? event.getDesignTemplate() : EventDesignTemplate.CLASSIC);
        model.addAttribute("previewTemplates", EventDesignTemplate.values());
        model.addAttribute("organizerPreview", true);
        model.addAttribute("locked", isLocked(event));
        model.addAttribute("signedIn", true);
        model.addAttribute("clientAccount", false);
        model.addAttribute("favoriteCount", 0L);
        return "events/marketplace-purchase";
    }

    @GetMapping("/{id}/edit")
    public String editEvent(@PathVariable Long id, Model model, Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        model.addAttribute("event", event);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("ticketTypes", ticketTypeRepository.findByEventIdOrderByPriceAsc(id));
        model.addAttribute("presentation", presentationSettingsRepository.findByEventId(id).orElse(null));
        model.addAttribute("editMode", true);
        return "dashboard/organizer/event-form";
    }

    @PostMapping("/{id}/edit")
    public String updateEvent(@PathVariable Long id,
                              @RequestParam String title,
                              @RequestParam String description,
                              @RequestParam Long categoryId,
                              @RequestParam String eventDate,
                              @RequestParam String location,
                              @RequestParam String city,
                              @RequestParam BigDecimal price,
                              @RequestParam Integer capacity,
                              @RequestParam(required = false) String imageUrl,
                              Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        if (isLocked(event)) {
            return "redirect:/dashboard/organizer/events/" + id + "?locked=true";
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
        LocalDateTime requestedDate = LocalDateTime.parse(eventDate);
        String changeSummary = buildEventChangeSummary(event, title, description, category, requestedDate, location, city, price, capacity, imageUrl);

        event.setTitle(title);
        event.setDescription(description);
        event.setCategory(category);
        event.setEventDate(requestedDate);
        event.setLocation(location);
        event.setCity(city);
        event.setPrice(price);
        event.setCapacity(capacity);
        event.setImageUrl(imageUrl == null ? "" : imageUrl);
        event.setStatus(EventStatus.PENDING_REVIEW);
        eventRepository.save(event);
        createRecord(event, EventAdminRecordType.UPDATED, changeSummary + "\n\nEvento devuelto a revisión administrativa.");
        return "redirect:/dashboard/organizer/events/" + id + "?updated=true";
    }

    @PostMapping("/{id}/request-support")
    public String requestSupport(@PathVariable Long id,
                                 @RequestParam Role targetRole,
                                 @RequestParam String subject,
                                 @RequestParam String body,
                                 @RequestParam(required = false, defaultValue = "NORMAL") MessagePriority priority,
                                 Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        if (isLocked(event)) {
            return "redirect:/dashboard/organizer/events/" + id + "?locked=true";
        }
        MessageType type = targetRole == Role.DESIGNER ? MessageType.DESIGN_REQUEST : MessageType.EVENT_REVIEW;
        String email = currentEmail(authentication);
        messageService.create("Organizador", email, Role.ORGANIZER, targetRole, type, priority, subject, body, id);
        createRecord(event, EventAdminRecordType.ADMIN_COPY, "Solicitud enviada a " + targetRole + ": " + subject);
        return "redirect:/dashboard/organizer/events/" + id + "?requestSent=true";
    }

    @GetMapping("/{id}/ticket-types")
    public String ticketTypes(@PathVariable Long id, Model model, Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        model.addAttribute("event", event);
        model.addAttribute("ticketTypes", ticketTypeRepository.findByEventIdOrderByPriceAsc(id));
        model.addAttribute("records", recordRepository.findByEventIdOrderByCreatedAtDesc(id));
        model.addAttribute("publishedEvent", event.getStatus() == EventStatus.PUBLISHED);
        return "dashboard/organizer/ticket-types";
    }

    @PostMapping("/{id}/ticket-types")
    public String createTicketType(@PathVariable Long id,
                                   @RequestParam(required = false) String namePreset,
                                   @RequestParam(required = false) String customName,
                                   @RequestParam String description,
                                   @RequestParam BigDecimal price,
                                   @RequestParam Integer quantityAvailable,
                                   Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        String finalName = resolveTicketTypeName(namePreset, customName);

        TicketType ticketType = new TicketType();
        ticketType.setEvent(event);
        ticketType.setName(finalName);
        ticketType.setDescription(description);
        ticketType.setPrice(price);
        ticketType.setQuantityAvailable(quantityAvailable);
        ticketType.setQuantitySold(0);
        ticketType.setActive(true);
        ticketTypeRepository.save(ticketType);
        synchronizeEventTicketSummary(event);
        keepPublishedOrRequestReview(event);
        eventRepository.save(event);
        createRecord(event, EventAdminRecordType.TICKET_TYPE_CREATED,
                "Entrada agregada por el organizador:\n\n" + ticketDescriptor(finalName, price, quantityAvailable, true)
                        + ticketManagementState(event));
        return "redirect:/dashboard/organizer/events/" + id + "/ticket-types?created=true";
    }

    @PostMapping("/{id}/ticket-types/{typeId}/update")
    public String updateTicketType(@PathVariable Long id,
                                   @PathVariable Long typeId,
                                   @RequestParam(required = false) String namePreset,
                                   @RequestParam(required = false) String customName,
                                   @RequestParam String description,
                                   @RequestParam BigDecimal price,
                                   @RequestParam Integer quantityAvailable,
                                   @RequestParam(required = false, defaultValue = "false") boolean active,
                                   Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        TicketType ticketType = ticketTypeRepository.findById(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de entrada no encontrado"));
        if (!ticketType.getEvent().getId().equals(event.getId())) {
            throw new AccessDeniedException("El tipo de entrada no pertenece a este evento");
        }
        int alreadySold = ticketType.getQuantitySold() == null ? 0 : ticketType.getQuantitySold();
        if (quantityAvailable < alreadySold) {
            return "redirect:/dashboard/organizer/events/" + id + "/ticket-types?quantityError=true&typeId=" + typeId;
        }

        String finalName = resolveTicketTypeName(namePreset, customName);
        String previousTicket = ticketDescriptor(ticketType.getName(), ticketType.getPrice(), ticketType.getQuantityAvailable(), ticketType.isActive());
        String requestedTicket = ticketDescriptor(finalName, price, quantityAvailable, active);

        ticketType.setName(finalName);
        ticketType.setDescription(description);
        ticketType.setPrice(price);
        ticketType.setQuantityAvailable(quantityAvailable);
        ticketType.setActive(active);
        ticketTypeRepository.save(ticketType);
        synchronizeEventTicketSummary(event);
        keepPublishedOrRequestReview(event);
        eventRepository.save(event);
        createRecord(event, EventAdminRecordType.TICKET_TYPE_UPDATED,
                "Entrada actualizada por el organizador:\n\nAntes: " + previousTicket + "\nDespués: " + requestedTicket
                        + ticketManagementState(event));
        return "redirect:/dashboard/organizer/events/" + id + "/ticket-types?updated=true";
    }

    @PostMapping("/{id}/delete")
    public String deleteEvent(@PathVariable Long id, Authentication authentication) {
        Event event = findOwnedEvent(id, authentication);
        if (isLocked(event) || !canOrganizerDelete(event)) {
            return "redirect:/dashboard/organizer/events/" + id + "?deleteDenied=true";
        }
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
        createRecord(event, EventAdminRecordType.CANCELLED_BY_ORGANIZER,
                "Evento eliminado por organizador dentro de las primeras 24 horas desde su creación. Se conserva trazabilidad administrativa.");
        return "redirect:/dashboard/organizer/events?deleted=true";
    }

    @GetMapping("/metrics")
    public String metrics(Model model, Authentication authentication) {
        List<Event> events = eventRepository.findByOrganizerEmailIgnoreCaseOrderByUpdatedAtDesc(currentEmail(authentication));
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        List<TicketType> allTicketTypes = eventIds.isEmpty()
                ? List.of()
                : ticketTypeRepository.findByEventIdInOrderByEventIdAscPriceAsc(eventIds);
        Map<Long, List<TicketType>> ticketTypesByEvent = new LinkedHashMap<>();
        allTicketTypes.forEach(type -> ticketTypesByEvent
                .computeIfAbsent(type.getEvent().getId(), ignored -> new ArrayList<>())
                .add(type));
        List<EventMetric> eventMetrics = events.stream()
                .map(event -> buildEventMetric(event, ticketTypesByEvent.getOrDefault(event.getId(), List.of())))
                .toList();
        int totalEvents = events.size();
        int totalCapacity = events.stream().map(Event::getCapacity).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int totalSold = events.stream().map(Event::getSold).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int availableTickets = Math.max(totalCapacity - totalSold, 0);
        double occupancyRate = totalCapacity == 0 ? 0 : totalSold * 100.0 / totalCapacity;
        BigDecimal revenue = eventMetrics.stream()
                .map(EventMetric::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("eventMetrics", eventMetrics);
        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("totalCapacity", totalCapacity);
        model.addAttribute("totalSold", totalSold);
        model.addAttribute("availableTickets", availableTickets);
        model.addAttribute("occupancyRate", String.format("%.1f", occupancyRate));
        model.addAttribute("revenue", revenue);
        return "dashboard/organizer/metrics";
    }

    private EventMetric buildEventMetric(Event event, List<TicketType> ticketTypes) {
        List<TicketMetric> breakdown = new ArrayList<>();
        int assignedSold = 0;
        int assignedCapacity = 0;

        for (TicketType type : ticketTypes) {
            int capacity = safeInt(type.getQuantityAvailable());
            int sold = safeInt(type.getQuantitySold());
            BigDecimal price = type.getPrice() == null ? BigDecimal.ZERO : type.getPrice();
            assignedCapacity += capacity;
            assignedSold += sold;
            breakdown.add(new TicketMetric(type.getName(), price, capacity, sold,
                    Math.max(capacity - sold, 0), price.multiply(BigDecimal.valueOf(sold)), type.isActive(), true));
        }

        int eventSold = safeInt(event.getSold());
        int unassignedSold = Math.max(eventSold - assignedSold, 0);
        if (ticketTypes.isEmpty() || unassignedSold > 0) {
            int capacity = ticketTypes.isEmpty()
                    ? safeInt(event.getCapacity())
                    : Math.max(safeInt(event.getCapacity()) - assignedCapacity, unassignedSold);
            BigDecimal price = safePrice(event);
            String name = ticketTypes.isEmpty() ? "Entrada general" : "Ventas sin tipo asociado";
            breakdown.add(new TicketMetric(name, price, capacity, unassignedSold,
                    Math.max(capacity - unassignedSold, 0), price.multiply(BigDecimal.valueOf(unassignedSold)), true, false));
        }

        BigDecimal eventRevenue = breakdown.stream()
                .map(TicketMetric::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EventMetric(event, List.copyOf(breakdown), eventRevenue);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private Event findOwnedEvent(Long id, Authentication authentication) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        if (event.getOrganizerEmail() == null || !event.getOrganizerEmail().equalsIgnoreCase(currentEmail(authentication))) {
            throw new AccessDeniedException("No tienes acceso a este evento");
        }
        return event;
    }

    private String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("Se requiere una sesión de organizador");
        }
        return authentication.getName();
    }

    private boolean canOrganizerDelete(Event event) {
        return event.getCreatedAt() != null && Duration.between(event.getCreatedAt(), LocalDateTime.now()).toHours() < 24;
    }

    private boolean isLocked(Event event) {
        return event.getStatus() == EventStatus.APPROVED || event.getStatus() == EventStatus.PUBLISHED;
    }

    private String resolveTicketTypeName(String namePreset, String customName) {
        if ("OTRO".equalsIgnoreCase(namePreset) && customName != null && !customName.isBlank()) {
            return customName.trim();
        }
        if (namePreset != null && !namePreset.isBlank()) {
            return namePreset.trim();
        }
        if (customName != null && !customName.isBlank()) {
            return customName.trim();
        }
        return "Entrada general";
    }

    private void keepPublishedOrRequestReview(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            event.setStatus(EventStatus.PENDING_REVIEW);
        }
    }

    private void synchronizeEventTicketSummary(Event event) {
        List<TicketType> activeTypes = ticketTypeRepository.findByEventIdOrderByPriceAsc(event.getId()).stream()
                .filter(TicketType::isActive)
                .toList();
        int totalCapacity = activeTypes.stream()
                .map(TicketType::getQuantityAvailable)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        BigDecimal lowestPrice = activeTypes.stream()
                .map(TicketType::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        if (totalCapacity > 0) {
            event.setCapacity(totalCapacity);
        }
        if (lowestPrice != null) {
            event.setPrice(lowestPrice);
        }
    }

    private String ticketManagementState(Event event) {
        return event.getStatus() == EventStatus.PUBLISHED
                ? "\n\nEl evento continúa publicado y la disponibilidad fue actualizada."
                : "\n\nEvento enviado a revisión administrativa.";
    }

    private void addMarketplacePreviewModel(Event event, EventPresentationSettings presentation, Model model) {
        model.addAttribute("event", event);
        model.addAttribute("ticketTypes", ticketTypeRepository.findByEventIdOrderByPriceAsc(event.getId()));
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
        return url.split("\\?")[0].replace("https://open.spotify.com/", "https://open.spotify.com/embed/")
                + "?utm_source=generator";
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

    private String displayNameFromEmail(String email) {
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String normalized = localPart.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        return normalized.isBlank() ? "Organizador" : normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
    }

    private String organizerDisplayName(String email) {
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email).orElse(null);
        if (account != null && hasText(account.getCompanyName())) {
            return account.getCompanyName().trim();
        }
        if (account != null && hasText(account.getFullName())) {
            return account.getFullName().trim();
        }
        return displayNameFromEmail(email);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildEventChangeSummary(Event event, String title, String description, Category category,
                                           LocalDateTime eventDate, String location, String city,
                                           BigDecimal price, Integer capacity, String imageUrl) {
        StringBuilder summary = new StringBuilder("Cambios solicitados por organizador:");
        appendChange(summary, "Título", event.getTitle(), title);
        appendChange(summary, "Descripción", event.getDescription(), description);
        appendChange(summary, "Categoría", categoryName(event.getCategory()), categoryName(category));
        appendChange(summary, "Fecha", formatDate(event.getEventDate()), formatDate(eventDate));
        appendChange(summary, "Ubicación", event.getLocation(), location);
        appendChange(summary, "Ciudad", event.getCity(), city);
        appendChange(summary, "Precio base", formatCurrency(event.getPrice()), formatCurrency(price));
        appendChange(summary, "Capacidad", value(event.getCapacity()), value(capacity));
        appendChange(summary, "Imagen principal", value(event.getImageUrl()), value(imageUrl));
        if (summary.length() == "Cambios solicitados por organizador:".length()) {
            summary.append("\n\nNo se detectaron cambios en los datos principales.");
        }
        return summary.toString();
    }

    private void appendChange(StringBuilder summary, String label, String before, String after) {
        String cleanBefore = value(before);
        String cleanAfter = value(after);
        if (!Objects.equals(cleanBefore, cleanAfter)) {
            summary.append("\n\n").append(label).append(":\nAntes: ").append(cleanBefore).append("\nDespués: ").append(cleanAfter);
        }
    }

    private String ticketDescriptor(String name, BigDecimal price, Integer quantity, boolean active) {
        return value(name) + " | " + formatCurrency(price) + " | Cantidad: " + value(quantity) + " | " + (active ? "Activo" : "Inactivo");
    }

    private String categoryName(Category category) {
        return category == null ? "Sin categoría" : value(category.getName());
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "Sin fecha" : BITACORA_DATE_FORMAT.format(value);
    }

    private String formatCurrency(BigDecimal value) {
        return value == null ? "S/ 0.00" : "S/ " + value;
    }

    private String value(Object value) {
        if (value == null) {
            return "No registrado";
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "No registrado" : text;
    }

    private void createRecord(Event event, EventAdminRecordType type, String description) {
        EventAdminRecord record = new EventAdminRecord();
        record.setEvent(event);
        record.setType(type);
        record.setDescription(description);
        recordRepository.save(record);
    }

    private BigDecimal safePrice(Event event) {
        return event.getPrice() == null ? BigDecimal.ZERO : event.getPrice();
    }

    public static final class EventMetric {
        private final Event event;
        private final List<TicketMetric> ticketTypes;
        private final BigDecimal revenue;

        private EventMetric(Event event, List<TicketMetric> ticketTypes, BigDecimal revenue) {
            this.event = event;
            this.ticketTypes = ticketTypes;
            this.revenue = revenue;
        }

        public Event getEvent() { return event; }
        public List<TicketMetric> getTicketTypes() { return ticketTypes; }
        public BigDecimal getRevenue() { return revenue; }
    }

    public static final class TicketMetric {
        private final String name;
        private final BigDecimal price;
        private final int capacity;
        private final int sold;
        private final int available;
        private final BigDecimal revenue;
        private final boolean active;
        private final boolean configured;

        private TicketMetric(String name, BigDecimal price, int capacity, int sold, int available,
                             BigDecimal revenue, boolean active, boolean configured) {
            this.name = name;
            this.price = price;
            this.capacity = capacity;
            this.sold = sold;
            this.available = available;
            this.revenue = revenue;
            this.active = active;
            this.configured = configured;
        }

        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
        public int getCapacity() { return capacity; }
        public int getSold() { return sold; }
        public int getAvailable() { return available; }
        public BigDecimal getRevenue() { return revenue; }
        public boolean isActive() { return active; }
        public boolean isConfigured() { return configured; }
    }
}
