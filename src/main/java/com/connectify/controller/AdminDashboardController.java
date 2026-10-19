package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.EventAdminRecord;
import com.connectify.entity.EventAdminRecordType;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.entity.MessagePriority;
import com.connectify.entity.MessageType;
import com.connectify.entity.Role;
import com.connectify.entity.TicketType;
import com.connectify.entity.UserAccount;
import com.connectify.repository.EventAdminRecordRepository;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.EventDetailViewModel;
import com.connectify.service.InternalMessageService;
import com.connectify.service.EventGateAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/dashboard/admin/events")
public class AdminDashboardController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventAdminRecordRepository recordRepository;
    private final EventPresentationSettingsRepository presentationSettingsRepository;
    private final UserAccountRepository userAccountRepository;
    private final InternalMessageService messageService;
    private final EventGateAccessService gateAccess;
    private final EventDetailViewModel eventDetailViewModel;

    public AdminDashboardController(EventRepository eventRepository,
                                    TicketTypeRepository ticketTypeRepository,
                                    EventAdminRecordRepository recordRepository,
                                    EventPresentationSettingsRepository presentationSettingsRepository,
                                    UserAccountRepository userAccountRepository,
                                    InternalMessageService messageService,
                                    EventGateAccessService gateAccess,
                                    EventDetailViewModel eventDetailViewModel) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.recordRepository = recordRepository;
        this.presentationSettingsRepository = presentationSettingsRepository;
        this.userAccountRepository = userAccountRepository;
        this.messageService = messageService;
        this.gateAccess = gateAccess;
        this.eventDetailViewModel = eventDetailViewModel;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Event event = findEvent(id);
        List<EventAdminRecord> records = safeRecords(id);
        model.addAttribute("eventId", event.getId());
        model.addAttribute("eventTitle", text(event.getTitle(), "Evento sin título"));
        model.addAttribute("eventDescription", text(event.getDescription(), "Sin descripción registrada."));
        model.addAttribute("eventStatus", event.getStatus() == null ? "SIN_ESTADO" : event.getStatus().name());
        model.addAttribute("eventCategory", safeCategory(event));
        model.addAttribute("eventDate", formatDate(event.getEventDate()));
        model.addAttribute("eventLocation", text(event.getLocation(), "Sin ubicación"));
        model.addAttribute("eventCity", text(event.getCity(), "Sin ciudad"));
        model.addAttribute("eventCapacity", number(event.getCapacity()));
        model.addAttribute("organizerName", text(event.getOrganizerName(), "Organizador registrado"));
        model.addAttribute("organizerEmail", text(event.getOrganizerEmail(), "Cuenta no registrada"));
        addOrganizerDetails(event, model);
        model.addAttribute("ticketLines", ticketLines(id));
        model.addAttribute("recordLines", recordLines(records));
        model.addAttribute("changeLines", changeLines(records));
        model.addAttribute("isReviewable", reviewable(event));
        model.addAttribute("isApproved", event.getStatus() == EventStatus.APPROVED);
        model.addAttribute("isPublished", event.getStatus() == EventStatus.PUBLISHED);
        model.addAttribute("isRejected", event.getStatus() == EventStatus.REJECTED);
        return "dashboard/admin/event-detail";
    }

    @GetMapping("/{id}/preview")
    public String preview(@PathVariable Long id, Model model) {
        Event event = findEvent(id);
        EventPresentationSettings presentation = presentationSettingsRepository.findByEventId(id).orElse(null);
        eventDetailViewModel.addAttributes(event, presentation, model);
        model.addAttribute("adminPreview", true);
        model.addAttribute("previewMode", true);
        model.addAttribute("organizerPreview", false);
        model.addAttribute("signedIn", true);
        model.addAttribute("clientAccount", false);
        model.addAttribute("favoriteSelected", false);
        model.addAttribute("favoriteCount", 0L);
        model.addAttribute("adminReturnUrl", "/dashboard/admin/events/" + id);
        return "events/marketplace-purchase";
    }

    private void addOrganizerDetails(Event event, Model model) {
        UserAccount account = safeOrganizerAccount(event.getOrganizerEmail());
        model.addAttribute("organizerCompany", text(account == null ? event.getOrganizerName() : account.getCompanyName(), "Sin razón social registrada"));
        model.addAttribute("organizerLegalContact", text(account == null ? null : account.getFullName(), text(event.getOrganizerName(), "Sin representante registrado")));
        model.addAttribute("organizerRuc", text(account == null ? null : account.getRuc(), "Sin RUC registrado"));
        model.addAttribute("organizerPhone", text(account == null ? null : account.getPhone(), "Sin teléfono registrado"));
        model.addAttribute("organizerAccountState", account == null ? "Cuenta no vinculada" : account.isActive() ? "Cuenta activa" : "Cuenta desactivada");
    }

    private UserAccount safeOrganizerAccount(String email) {
        if (email == null || email.isBlank()) return null;
        try { return userAccountRepository.findByEmailIgnoreCase(email).orElse(null); }
        catch (RuntimeException ex) { return null; }
    }

    @PostMapping("/{id}/observe")
    public String observe(@PathVariable Long id, @RequestParam(defaultValue = "Evento observado. Requiere ajustes del organizador.") String note,
                          @RequestParam(defaultValue = "false") boolean notifyDesigner) {
        Event event = findEvent(id);
        if (!reviewable(event)) return denied(id);
        event.setStatus(EventStatus.OBSERVED);
        event.setDesignEnabled(notifyDesigner);
        eventRepository.save(event);
        record(event, "Observado: " + note);
        messageService.create("Administrador", "admin@connectify.com", Role.ADMIN, Role.ORGANIZER, MessageType.EVENT_REVIEW, MessagePriority.HIGH,
                "Observación administrativa: " + event.getTitle(), note, id);
        if (notifyDesigner) {
            messageService.create("Administrador", "admin@connectify.com", Role.ADMIN, Role.DESIGNER, MessageType.DESIGN_FEEDBACK, MessagePriority.NORMAL,
                    "Ajuste visual solicitado: " + event.getTitle(), note, id);
        }
        return "redirect:/dashboard/admin/events/" + id + "?observed=true";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(defaultValue = "Construcción, datos y entradas conformes.") String note) {
        Event event = findEvent(id);
        if (!reviewable(event)) return denied(id);
        event.setStatus(EventStatus.APPROVED);
        event.setGateAccessCode(gateAccess.ensureEventCode(id));
        event.setDesignEnabled(false);
        eventRepository.save(event);
        record(event, "Aprobado: " + note + " El evento queda bloqueado para edición.");
        messageService.create("Administrador", "admin@connectify.com", Role.ADMIN, Role.ORGANIZER, MessageType.EVENT_REVIEW, MessagePriority.NORMAL,
                "Evento aprobado: " + event.getTitle(), "La construcción fue aprobada y quedó bloqueada. Aún no está publicada.", id);
        return "redirect:/dashboard/admin/events/" + id + "?approved=true";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(defaultValue = "La propuesta no cumple las condiciones de aprobación.") String note,
                         @RequestParam(defaultValue = "false") boolean notifyDesigner) {
        Event event = findEvent(id);
        if (!reviewable(event)) return denied(id);
        event.setStatus(EventStatus.REJECTED);
        event.setDesignEnabled(false);
        eventRepository.save(event);
        record(event, "Rechazado: " + note);
        messageService.create("Administrador", "admin@connectify.com", Role.ADMIN, Role.ORGANIZER, MessageType.EVENT_REVIEW, MessagePriority.HIGH,
                "Propuesta rechazada: " + event.getTitle(), note, id);
        if (notifyDesigner) {
            messageService.create("Administrador", "admin@connectify.com", Role.ADMIN, Role.DESIGNER, MessageType.DESIGN_FEEDBACK, MessagePriority.NORMAL,
                    "Propuesta rechazada: " + event.getTitle(), note, id);
        }
        return "redirect:/dashboard/admin/events/" + id + "?rejected=true";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id) {
        Event event = findEvent(id);
        if (event.getStatus() != EventStatus.APPROVED) return "redirect:/dashboard/admin/events/" + id + "?publishDenied=true";
        event.setStatus(EventStatus.PUBLISHED);
        event.setGateAccessCode(gateAccess.ensureEventCode(id));
        eventRepository.save(event);
        record(event, "Publicado en marketplace.");
        return "redirect:/dashboard/admin/events/" + id + "?published=true";
    }

    @PostMapping("/{id}/unpublish")
    public String unpublish(@PathVariable Long id) {
        Event event = findEvent(id);
        if (event.getStatus() != EventStatus.PUBLISHED) return "redirect:/dashboard/admin/events/" + id + "?unpublishDenied=true";
        event.setStatus(EventStatus.APPROVED);
        eventRepository.save(event);
        record(event, "Despublicado. El evento queda aprobado y bloqueado.");
        return "redirect:/dashboard/admin/events/" + id + "?unpublished=true";
    }

    private List<EventAdminRecord> safeRecords(Long id) {
        try { return recordRepository.findByEventIdOrderByCreatedAtDesc(id); }
        catch (RuntimeException ex) { return new ArrayList<>(); }
    }

    private List<TicketType> safeTicketTypes(Long id) {
        try { return ticketTypeRepository.findByEventIdOrderByPriceAsc(id); }
        catch (RuntimeException ex) { return new ArrayList<>(); }
    }

    private List<String> ticketLines(Long id) {
        List<String> lines = new ArrayList<>();
        for (TicketType ticket : safeTicketTypes(id)) {
            if (ticket == null) continue;
            lines.add(text(ticket.getName(), "Entrada") + " | S/ " + money(ticket.getPrice()) + " | Cantidad: " + number(ticket.getQuantityAvailable()) + " | Vendidas: " + number(ticket.getQuantitySold()) + " | Disponibles: " + number(ticket.getRemaining()));
        }
        return lines;
    }

    private List<String> recordLines(List<EventAdminRecord> records) {
        List<String> lines = new ArrayList<>();
        for (EventAdminRecord record : records) {
            if (record == null) continue;
            lines.add(formatDate(record.getCreatedAt()) + " — " + text(record.getDescription(), "Registro sin descripción"));
        }
        return lines;
    }

    private List<String> changeLines(List<EventAdminRecord> records) {
        List<String> lines = new ArrayList<>();
        for (EventAdminRecord record : records) {
            if (isChangeRecord(record)) {
                lines.add(formatDate(record.getCreatedAt()) + " — " + text(record.getDescription(), "Cambio sin descripción"));
            }
        }
        return lines;
    }

    private boolean isChangeRecord(EventAdminRecord record) {
        return record != null && record.getType() != null && (
                record.getType() == EventAdminRecordType.UPDATED ||
                record.getType() == EventAdminRecordType.TICKET_TYPE_CREATED ||
                record.getType() == EventAdminRecordType.TICKET_TYPE_UPDATED
        );
    }

    private String safeCategory(Event event) {
        try { return event.getCategory() == null ? "Sin categoría" : text(event.getCategory().getName(), "Sin categoría"); }
        catch (RuntimeException ex) { return "Sin categoría"; }
    }

    private String formatDate(LocalDateTime date) { return date == null ? "Sin fecha" : DATE_FORMAT.format(date); }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String number(Integer value) { return String.valueOf(value == null ? 0 : value); }
    private String money(BigDecimal value) { return value == null ? "0.00" : value.toPlainString(); }
    private boolean reviewable(Event event) { return event.getStatus() == EventStatus.PENDING_REVIEW || event.getStatus() == EventStatus.OBSERVED; }
    private String denied(Long id) { return "redirect:/dashboard/admin/events/" + id + "?decisionDenied=true"; }
    private Event findEvent(Long id) { return eventRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Evento no encontrado")); }
    private void record(Event event, String description) {
        EventAdminRecord record = new EventAdminRecord();
        record.setEvent(event); record.setType(EventAdminRecordType.ADMIN_COPY); record.setDescription(description); record.setCreatedAt(LocalDateTime.now());
        recordRepository.save(record);
    }
}
