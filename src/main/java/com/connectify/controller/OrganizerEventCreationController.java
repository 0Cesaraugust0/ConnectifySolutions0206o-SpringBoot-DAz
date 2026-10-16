package com.connectify.controller;

import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventAdminRecord;
import com.connectify.entity.EventAdminRecordType;
import com.connectify.entity.EventDesignTemplate;
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
import com.connectify.service.ImageStorageService;
import com.connectify.service.InternalMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/dashboard/organizer/events")
public class OrganizerEventCreationController {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventAdminRecordRepository recordRepository;
    private final EventPresentationSettingsRepository presentationSettingsRepository;
    private final InternalMessageService messageService;
    private final UserAccountRepository userAccountRepository;
    private final ImageStorageService imageStorageService;

    public OrganizerEventCreationController(EventRepository eventRepository,
                                            CategoryRepository categoryRepository,
                                            TicketTypeRepository ticketTypeRepository,
                                            EventAdminRecordRepository recordRepository,
                                            EventPresentationSettingsRepository presentationSettingsRepository,
                                            InternalMessageService messageService,
                                            UserAccountRepository userAccountRepository,
                                            ImageStorageService imageStorageService) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.recordRepository = recordRepository;
        this.presentationSettingsRepository = presentationSettingsRepository;
        this.messageService = messageService;
        this.userAccountRepository = userAccountRepository;
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/create-with-tickets")
    public String createWithTickets(@RequestParam String title,
                                    @RequestParam String description,
                                    @RequestParam Long categoryId,
                                    @RequestParam String eventDate,
                                    @RequestParam String location,
                                    @RequestParam String city,
                                    @RequestParam BigDecimal price,
                                    @RequestParam Integer capacity,
                                    @RequestParam(required = false) String imageUrl,
                                    @RequestParam(required = false) MultipartFile imageFile,
                                    @RequestParam(defaultValue = "CLASSIC") EventDesignTemplate designTemplate,
                                    @RequestParam(required = false) String backgroundImageUrl,
                                    @RequestParam(required = false) String organizerBrandName,
                                    @RequestParam(required = false) String organizerLogoUrl,
                                    @RequestParam(required = false) String highlightText,
                                    @RequestParam(required = false, defaultValue = "false") boolean floatingSalesCard,
                                    @RequestParam(required = false, defaultValue = "false") boolean showSponsors,
                                    @RequestParam(required = false) String sponsorsText,
                                    @RequestParam(required = false) String sponsorLogosText,
                                    @RequestParam(required = false, defaultValue = "false") boolean showMedia,
                                    @RequestParam(required = false) String youtubeUrl,
                                    @RequestParam(required = false) String spotifyUrl,
                                    @RequestParam(required = false, defaultValue = "false") boolean showStageMap,
                                    @RequestParam(required = false) String stageImageUrl,
                                    @RequestParam(required = false) MultipartFile stageImageFile,
                                    @RequestParam(required = false) String stageText,
                                    @RequestParam(required = false, defaultValue = "false") boolean showSessions,
                                    @RequestParam(required = false) String sessionText,
                                    @RequestParam(required = false) List<String> ticketName,
                                    @RequestParam(required = false) List<BigDecimal> ticketPrice,
                                    @RequestParam(required = false) List<Integer> ticketQuantity,
                                    @RequestParam(required = false) List<String> ticketDescription,
                                    @RequestParam(required = false, defaultValue = "false") boolean requestDesigner,
                                    @RequestParam(required = false) String designBrief,
                                    @ModelAttribute PresentationOptions visualOptions,
                                    Authentication authentication) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        String organizerEmail = currentEmail(authentication);
        Event event = new Event();
        event.setTitle(title.trim());
        event.setDescription(description.trim());
        event.setCategory(category);
        event.setEventDate(LocalDateTime.parse(eventDate));
        event.setLocation(location.trim());
        event.setCity(city.trim());
        event.setOrganizerName(organizerDisplayName(organizerEmail));
        event.setOrganizerEmail(organizerEmail);
        event.setPrice(price);
        event.setCapacity(capacity);
        event.setSold(0);
        event.setImageUrl(firstText(imageStorageService.store(imageFile, "presentations"), imageUrl));
        event.setFeatured(false);
        event.setDesignTemplate(designTemplate);
        event.setDesignEnabled(requestDesigner);
        event.setStatus(EventStatus.PENDING_REVIEW);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());

        Event saved = eventRepository.save(event);
        saveOrganizerPresentation(saved, designTemplate, backgroundImageUrl, organizerBrandName, organizerLogoUrl,
                highlightText, floatingSalesCard, showSponsors, sponsorsText, sponsorLogosText, showMedia,
                youtubeUrl, spotifyUrl, showStageMap, stageImageUrl, stageImageFile, stageText, showSessions, sessionText, visualOptions);
        createRecord(saved, EventAdminRecordType.CREATED,
                "Evento creado por organizador y enviado a revisión administrativa.\n"
                        + "Plantilla inicial de Marketplace: " + designTemplate.getLabel() + ".");

        TicketSummary ticketSummary = createTicketTypes(saved, ticketName, ticketPrice, ticketQuantity, ticketDescription);
        if (ticketSummary.createdCount() > 0) {
            if (ticketSummary.lowestPrice() != null) {
                saved.setPrice(ticketSummary.lowestPrice());
            }
            if (ticketSummary.totalQuantity() > saved.getCapacity()) {
                saved.setCapacity(ticketSummary.totalQuantity());
            }
            eventRepository.save(saved);
            createRecord(saved, EventAdminRecordType.TICKET_TYPE_CREATED,
                    "Tipos de entrada creados durante la construcción inicial:\n\n" + ticketSummary.description());
        }

        if (requestDesigner) {
            String brief = hasText(designBrief)
                    ? designBrief.trim()
                    : "Solicito una propuesta visual para presentar este evento en el marketplace.";
            messageService.create("Organizador", organizerEmail, Role.ORGANIZER, Role.DESIGNER,
                    MessageType.DESIGN_REQUEST, MessagePriority.NORMAL,
                    "Solicitud visual: " + saved.getTitle(), brief, saved.getId());
            createRecord(saved, EventAdminRecordType.ADMIN_COPY,
                    "Solicitud visual enviada al Diseñador. Base seleccionada por el Organizador: "
                            + designTemplate.getLabel() + ".");
        }

        return "redirect:/dashboard/organizer/events/" + saved.getId() + "?created=true";
    }

    @PostMapping("/{id}/edit-all")
    public String updateCompleteEvent(@org.springframework.web.bind.annotation.PathVariable Long id,
                                      @RequestParam String title,
                                      @RequestParam String description,
                                      @RequestParam Long categoryId,
                                      @RequestParam String eventDate,
                                      @RequestParam String location,
                                      @RequestParam String city,
                                      @RequestParam BigDecimal price,
                                      @RequestParam Integer capacity,
                                      @RequestParam(required = false) String imageUrl,
                                      @RequestParam(required = false) MultipartFile imageFile,
                                      @RequestParam(defaultValue = "CLASSIC") EventDesignTemplate designTemplate,
                                      @RequestParam(required = false) String backgroundImageUrl,
                                      @RequestParam(required = false) String organizerBrandName,
                                      @RequestParam(required = false) String organizerLogoUrl,
                                      @RequestParam(required = false) String highlightText,
                                      @RequestParam(required = false, defaultValue = "false") boolean floatingSalesCard,
                                      @RequestParam(required = false, defaultValue = "false") boolean showSponsors,
                                      @RequestParam(required = false) String sponsorsText,
                                      @RequestParam(required = false) String sponsorLogosText,
                                      @RequestParam(required = false, defaultValue = "false") boolean showMedia,
                                      @RequestParam(required = false) String youtubeUrl,
                                      @RequestParam(required = false) String spotifyUrl,
                                      @RequestParam(required = false, defaultValue = "false") boolean showStageMap,
                                      @RequestParam(required = false) String stageImageUrl,
                                      @RequestParam(required = false) MultipartFile stageImageFile,
                                      @RequestParam(required = false) String stageText,
                                      @RequestParam(required = false, defaultValue = "false") boolean showSessions,
                                      @RequestParam(required = false) String sessionText,
                                      @RequestParam(required = false) List<String> ticketId,
                                      @RequestParam(required = false) List<String> ticketName,
                                      @RequestParam(required = false) List<BigDecimal> ticketPrice,
                                      @RequestParam(required = false) List<Integer> ticketQuantity,
                                      @RequestParam(required = false) List<String> ticketDescription,
                                      @RequestParam(required = false, defaultValue = "false") boolean requestDesigner,
                                      @RequestParam(required = false) String designBrief,
                                      @RequestParam(defaultValue = "save") String workflowAction,
                                      @ModelAttribute PresentationOptions visualOptions,
                                      Authentication authentication) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        String organizerEmail = currentEmail(authentication);
        if (event.getOrganizerEmail() == null || !event.getOrganizerEmail().equalsIgnoreCase(organizerEmail)) {
            throw new AccessDeniedException("El evento no pertenece al organizador autenticado");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
        boolean firstDesignerRequest = requestDesigner && !event.isDesignEnabled();

        event.setTitle(title.trim());
        event.setDescription(description.trim());
        event.setCategory(category);
        event.setEventDate(LocalDateTime.parse(eventDate));
        event.setLocation(location.trim());
        event.setCity(city.trim());
        event.setPrice(price);
        event.setCapacity(capacity);
        event.setImageUrl(firstText(imageStorageService.store(imageFile, "presentations"), firstText(imageUrl, event.getImageUrl())));
        event.setDesignTemplate(designTemplate);
        event.setDesignEnabled(requestDesigner);
        event.setUpdatedAt(LocalDateTime.now());

        saveOrganizerPresentation(event, designTemplate, backgroundImageUrl, organizerBrandName, organizerLogoUrl,
                highlightText, floatingSalesCard, showSponsors, sponsorsText, sponsorLogosText, showMedia,
                youtubeUrl, spotifyUrl, showStageMap, stageImageUrl, stageImageFile, stageText, showSessions, sessionText, visualOptions);

        TicketSummary ticketSummary = updateTicketTypes(event, ticketId, ticketName, ticketPrice, ticketQuantity, ticketDescription);
        if (ticketSummary.createdCount() > 0) {
            event.setCapacity(Math.max(ticketSummary.totalQuantity(), event.getSold() == null ? 0 : event.getSold()));
            if (ticketSummary.lowestPrice() != null) {
                event.setPrice(ticketSummary.lowestPrice());
            }
        }

        boolean submitForReview = "submit_review".equalsIgnoreCase(workflowAction);
        event.setStatus(submitForReview ? EventStatus.PENDING_REVIEW : EventStatus.DRAFT);
        eventRepository.save(event);
        createRecord(event, EventAdminRecordType.UPDATED,
                submitForReview
                        ? "Evento actualizado integralmente y enviado a revisión administrativa.\n\n" + ticketSummary.description()
                        : "Cambios integrales guardados como borrador por el organizador.\n\n" + ticketSummary.description());

        if (firstDesignerRequest) {
            String brief = hasText(designBrief)
                    ? designBrief.trim()
                    : "Solicito una propuesta visual para presentar este evento en el marketplace.";
            messageService.create("Organizador", organizerEmail, Role.ORGANIZER, Role.DESIGNER,
                    MessageType.DESIGN_REQUEST, MessagePriority.NORMAL,
                    "Solicitud visual: " + event.getTitle(), brief, event.getId());
            createRecord(event, EventAdminRecordType.ADMIN_COPY,
                    "Solicitud visual enviada al Diseñador desde la edición integral del evento.");
        }

        return "redirect:/dashboard/organizer/events/" + id + "/edit?" + (submitForReview ? "submitted=true" : "saved=true");
    }

    private TicketSummary updateTicketTypes(Event event,
                                            List<String> ticketIds,
                                            List<String> names,
                                            List<BigDecimal> prices,
                                            List<Integer> quantities,
                                            List<String> descriptions) {
        if (names == null || prices == null || quantities == null) {
            return new TicketSummary(0, 0, null, "Sin variantes activas.");
        }

        int rows = Math.min(names.size(), Math.min(prices.size(), quantities.size()));
        List<Long> retainedIds = new ArrayList<>();
        int activeRows = 0;
        int totalQuantity = 0;
        BigDecimal lowestPrice = null;
        StringBuilder summary = new StringBuilder("Entradas guardadas:");

        for (int index = 0; index < rows; index++) {
            String name = names.get(index);
            BigDecimal rowPrice = prices.get(index);
            Integer quantity = quantities.get(index);
            if (!hasText(name) || rowPrice == null || quantity == null || quantity < 1 || rowPrice.signum() < 0) {
                continue;
            }

            Long existingId = parseId(ticketIds, index);
            TicketType ticket = existingId == null ? new TicketType() : ticketTypeRepository.findById(existingId)
                    .orElseThrow(() -> new IllegalArgumentException("Tipo de entrada no encontrado"));
            if (ticket.getEvent() != null && !ticket.getEvent().getId().equals(event.getId())) {
                throw new AccessDeniedException("El tipo de entrada no pertenece a este evento");
            }
            int alreadySold = ticket.getQuantitySold() == null ? 0 : ticket.getQuantitySold();
            if (quantity < alreadySold) {
                throw new IllegalArgumentException("Los cupos no pueden ser menores que las entradas ya vendidas de " + name);
            }

            ticket.setEvent(event);
            ticket.setName(name.trim());
            ticket.setDescription(descriptions != null && index < descriptions.size() && hasText(descriptions.get(index))
                    ? descriptions.get(index).trim() : "Entrada " + name.trim() + " para " + event.getTitle());
            ticket.setPrice(rowPrice);
            ticket.setQuantityAvailable(quantity);
            ticket.setQuantitySold(alreadySold);
            if (existingId == null) {
                ticket.setActive(true);
            }
            ticket = ticketTypeRepository.save(ticket);
            retainedIds.add(ticket.getId());

            if (ticket.isActive()) {
                activeRows++;
                totalQuantity += quantity;
                lowestPrice = lowestPrice == null || rowPrice.compareTo(lowestPrice) < 0 ? rowPrice : lowestPrice;
            }
            summary.append("\n• ").append(name.trim()).append(" | S/ ").append(rowPrice)
                    .append(" | Cupos: ").append(quantity).append(" | Vendidas: ").append(alreadySold);
        }

        for (TicketType previous : ticketTypeRepository.findByEventIdOrderByPriceAsc(event.getId())) {
            if (!retainedIds.contains(previous.getId())) {
                previous.setActive(false);
                ticketTypeRepository.save(previous);
            }
        }
        return new TicketSummary(activeRows, totalQuantity, lowestPrice, summary.toString());
    }

    private Long parseId(List<String> ids, int index) {
        if (ids == null || index >= ids.size() || !hasText(ids.get(index))) {
            return null;
        }
        try {
            return Long.valueOf(ids.get(index));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Identificador de entrada inválido", ex);
        }
    }

    private TicketSummary createTicketTypes(Event event, List<String> names, List<BigDecimal> prices,
                                            List<Integer> quantities, List<String> descriptions) {
        if (names == null || prices == null || quantities == null) {
            return new TicketSummary(0, 0, null, "");
        }

        int rows = Math.min(names.size(), Math.min(prices.size(), quantities.size()));
        int created = 0;
        int totalQuantity = 0;
        BigDecimal lowestPrice = null;
        StringBuilder description = new StringBuilder();

        for (int index = 0; index < rows; index++) {
            String name = names.get(index);
            BigDecimal ticketPrice = prices.get(index);
            Integer quantity = quantities.get(index);
            if (!hasText(name) || ticketPrice == null || quantity == null || quantity < 1 || ticketPrice.signum() < 0) {
                continue;
            }

            String ticketDescription = descriptions != null && index < descriptions.size() && hasText(descriptions.get(index))
                    ? descriptions.get(index).trim()
                    : "Entrada " + name.trim() + " para " + event.getTitle();

            TicketType ticket = new TicketType();
            ticket.setEvent(event);
            ticket.setName(name.trim());
            ticket.setDescription(ticketDescription);
            ticket.setPrice(ticketPrice);
            ticket.setQuantityAvailable(quantity);
            ticket.setQuantitySold(0);
            ticket.setActive(true);
            ticketTypeRepository.save(ticket);

            created++;
            totalQuantity += quantity;
            lowestPrice = lowestPrice == null || ticketPrice.compareTo(lowestPrice) < 0 ? ticketPrice : lowestPrice;
            if (description.length() > 0) {
                description.append("\n");
            }
            description.append("• ").append(name.trim()).append(" | S/ ").append(ticketPrice)
                    .append(" | Cupos: ").append(quantity);
        }

        return new TicketSummary(created, totalQuantity, lowestPrice, description.toString());
    }

    private void saveOrganizerPresentation(Event event,
                                           EventDesignTemplate designTemplate,
                                           String backgroundImageUrl,
                                           String organizerBrandName,
                                           String organizerLogoUrl,
                                           String highlightText,
                                           boolean floatingSalesCard,
                                           boolean showSponsors,
                                           String sponsorsText,
                                           String sponsorLogosText,
                                           boolean showMedia,
                                           String youtubeUrl,
                                           String spotifyUrl,
                                           boolean showStageMap,
                                           String stageImageUrl,
                                           MultipartFile stageImageFile,
                                           String stageText,
                                           boolean showSessions,
                                           String sessionText,
                                           PresentationOptions visualOptions) {
        EventPresentationSettings presentation = presentationSettingsRepository.findByEventId(event.getId())
                .orElseGet(() -> {
                    EventPresentationSettings created = new EventPresentationSettings();
                    created.setEvent(event);
                    return created;
                });
        presentation.setBackgroundImageUrl(safeUrl(backgroundImageUrl));
        presentation.setOrganizerBrandName(optionalText(organizerBrandName));
        presentation.setOrganizerLogoUrl(safeUrl(organizerLogoUrl));
        presentation.setHighlightText(optionalText(highlightText));
        if (visualOptions.getInfoPosition() != null) {
            presentation.setInfoPosition(safeChoice(visualOptions.getInfoPosition(), List.of("STANDARD", "SIDEBAR", "FLOATING"), presentation.getInfoPosition()));
        } else if (floatingSalesCard) {
            presentation.setInfoPosition("FLOATING");
        }
        presentation.setShowSponsors(showSponsors);
        presentation.setSponsorsText(optionalText(sponsorsText));
        presentation.setSponsorLogosText(optionalText(sponsorLogosText));
        presentation.setShowMedia(showMedia);
        presentation.setYoutubeUrl(safeUrl(youtubeUrl));
        presentation.setSpotifyUrl(safeUrl(spotifyUrl));
        presentation.setShowStageMap(showStageMap);
        presentation.setStageImageUrl(firstText(imageStorageService.store(stageImageFile, "presentations"),
                firstText(safeUrl(stageImageUrl), presentation.getStageImageUrl())));
        presentation.setStageText(optionalText(stageText));
        presentation.setShowSessions(showSessions);
        presentation.setSessionText(optionalText(sessionText));
        if (visualOptions.getPrimaryColor() != null) presentation.setPrimaryColor(safeColor(visualOptions.getPrimaryColor(), presentation.getPrimaryColor()));
        if (visualOptions.getAccentColor() != null) presentation.setAccentColor(safeColor(visualOptions.getAccentColor(), presentation.getAccentColor()));
        if (visualOptions.getCoverImageUrl() != null) presentation.setCoverImageUrl(safeUrl(visualOptions.getCoverImageUrl()));
        if (visualOptions.getThumbnailImageUrl() != null) presentation.setThumbnailImageUrl(safeUrl(visualOptions.getThumbnailImageUrl()));
        if (visualOptions.getCoverFocus() != null) presentation.setCoverFocus(safeChoice(visualOptions.getCoverFocus(), List.of("TOP", "CENTER", "BOTTOM"), presentation.getCoverFocus()));
        if (visualOptions.getContentFocus() != null) presentation.setContentFocus(safeChoice(visualOptions.getContentFocus(), List.of("BALANCED", "SALES", "INFORMATION"), presentation.getContentFocus()));
        if (visualOptions.getShowGallery() != null) presentation.setShowGallery(visualOptions.getShowGallery());
        if (visualOptions.getShowOrganizer() != null) presentation.setShowOrganizer(visualOptions.getShowOrganizer());
        if (visualOptions.getShowAgenda() != null) presentation.setShowAgenda(visualOptions.getShowAgenda());
        if (visualOptions.getAgendaText() != null) presentation.setAgendaText(optionalText(visualOptions.getAgendaText()));
        if (visualOptions.getShowBenefits() != null) presentation.setShowBenefits(visualOptions.getShowBenefits());
        if (visualOptions.getBenefitsText() != null) presentation.setBenefitsText(optionalText(visualOptions.getBenefitsText()));
        presentationSettingsRepository.save(presentation);
    }

    private String safeChoice(String value, List<String> choices, String fallback) {
        return choices.contains(value) ? value : fallback;
    }

    private String safeColor(String value, String fallback) {
        return value != null && value.matches("#[0-9a-fA-F]{6}") ? value : fallback;
    }

    /** Optional fields preserve existing designer settings when an older form omits them. */
    public static class PresentationOptions {
        private String primaryColor, accentColor, coverImageUrl, thumbnailImageUrl, coverFocus, contentFocus, infoPosition;
        private Boolean showGallery, showOrganizer, showAgenda, showBenefits;
        private String agendaText, benefitsText;
        public String getPrimaryColor() { return primaryColor; }
        public void setPrimaryColor(String value) { primaryColor = value; }
        public String getAccentColor() { return accentColor; }
        public void setAccentColor(String value) { accentColor = value; }
        public String getCoverImageUrl() { return coverImageUrl; }
        public void setCoverImageUrl(String value) { coverImageUrl = value; }
        public String getThumbnailImageUrl() { return thumbnailImageUrl; }
        public void setThumbnailImageUrl(String value) { thumbnailImageUrl = value; }
        public String getCoverFocus() { return coverFocus; }
        public void setCoverFocus(String value) { coverFocus = value; }
        public String getContentFocus() { return contentFocus; }
        public void setContentFocus(String value) { contentFocus = value; }
        public String getInfoPosition() { return infoPosition; }
        public void setInfoPosition(String value) { infoPosition = value; }
        public Boolean getShowGallery() { return showGallery; }
        public void setShowGallery(Boolean value) { showGallery = value; }
        public Boolean getShowOrganizer() { return showOrganizer; }
        public void setShowOrganizer(Boolean value) { showOrganizer = value; }
        public Boolean getShowAgenda() { return showAgenda; }
        public void setShowAgenda(Boolean value) { showAgenda = value; }
        public Boolean getShowBenefits() { return showBenefits; }
        public void setShowBenefits(Boolean value) { showBenefits = value; }
        public String getAgendaText() { return agendaText; }
        public void setAgendaText(String value) { agendaText = value; }
        public String getBenefitsText() { return benefitsText; }
        public void setBenefitsText(String value) { benefitsText = value; }
    }

    private String currentEmail(Authentication authentication) {
        if (authentication == null || !hasText(authentication.getName())) {
            throw new IllegalStateException("Se requiere una sesión de organizador");
        }
        return authentication.getName();
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

    private String firstText(String preferred, String fallback) {
        if (hasText(preferred)) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private String optionalText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String safeUrl(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.startsWith("https://") || normalized.startsWith("http://") || normalized.startsWith("/uploads/")
                || normalized.startsWith("/images/showcase/") && !normalized.contains("..") && !normalized.contains("\\")
                ? normalized : null;
    }

    private void createRecord(Event event, EventAdminRecordType type, String description) {
        EventAdminRecord record = new EventAdminRecord();
        record.setEvent(event);
        record.setType(type);
        record.setDescription(description);
        record.setCreatedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private record TicketSummary(int createdCount, int totalQuantity, BigDecimal lowestPrice, String description) {
    }
}
