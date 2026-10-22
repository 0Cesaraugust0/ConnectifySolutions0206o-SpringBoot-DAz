package com.connectify.controller;

import com.connectify.entity.InternalMessage;
import com.connectify.entity.MessagePriority;
import com.connectify.entity.MessageStatus;
import com.connectify.entity.MessageType;
import com.connectify.entity.Role;
import com.connectify.service.EventService;
import com.connectify.service.InternalMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/communications")
public class CommunicationController {

    private final InternalMessageService messageService;
    private final EventService eventService;

    public CommunicationController(InternalMessageService messageService, EventService eventService) {
        this.messageService = messageService;
        this.eventService = eventService;
    }

    @GetMapping
    public String inbox(@RequestParam(required = false) String box,
                        @RequestParam(required = false) Role from,
                        Authentication authentication,
                        Model model) {
        Role currentRole = currentRole(authentication);
        String email = authentication != null ? authentication.getName() : "system@connectify.com";

        if (currentRole == Role.CLIENT) {
            List<InternalMessage> customerMessages = messageService.sent(email).stream()
                    .filter(this::isCustomerSupport)
                    .toList();
            model.addAttribute("supportMode", true);
            model.addAttribute("role", Role.CLIENT);
            model.addAttribute("messages", customerMessages);
            model.addAttribute("unread", 0);
            model.addAttribute("currentRole", currentRole);
            model.addAttribute("selectedSource", currentRole);
            model.addAttribute("roleLabel", "Buzón de Mensajería");
            model.addAttribute("roleDescription", "Consultas del Cliente dirigidas únicamente a Administración.");
            model.addAttribute("customerInboxMode", false);
            model.addAttribute("operationsMode", false);
            model.addAttribute("sentMode", false);
            return "communications/inbox";
        }

        Role inboxRole = currentRole;
        boolean sentMode = "sent".equalsIgnoreCase(box);
        List<Role> sourceRoles = sourceRolesFor(currentRole);
        Role requestedSource = !sentMode && currentRole == Role.ADMIN && "customers".equalsIgnoreCase(box)
                ? Role.CLIENT : from;
        Role selectedSource = requestedSource != null && sourceRoles.contains(requestedSource)
                ? requestedSource : currentRole;
        List<InternalMessage> messages = sentMode
                ? messageService.sent(email)
                : messageService.inbox(inboxRole);
        boolean customerInboxMode = !sentMode && currentRole == Role.ADMIN
                && selectedSource == Role.CLIENT;
        boolean operationsMode = !sentMode && currentRole == Role.ADMIN
                && !customerInboxMode
                && ("operations".equalsIgnoreCase(box) || selectedSource != Role.ADMIN);

        if (sentMode) {
            // La consulta ya está limitada al remitente autenticado.
        } else if (customerInboxMode) {
            messages = messages.stream().filter(this::isCustomerSupport).toList();
        } else if (selectedSource != currentRole) {
            messages = messages.stream().filter(message -> message.getSenderRole() == selectedSource).toList();
        } else if (operationsMode) {
            messages = messages.stream().filter(message -> !isCustomerSupport(message)).toList();
        }

        List<MessageSourceOption> sourceOptions = sourceRoles.stream()
                .map(source -> new MessageSourceOption(source,
                        source == currentRole ? "Todos" : senderLabel(source)))
                .toList();

        model.addAttribute("supportMode", false);
        model.addAttribute("role", inboxRole);
        model.addAttribute("currentRole", currentRole);
        model.addAttribute("customerInboxMode", customerInboxMode);
        model.addAttribute("operationsMode", operationsMode);
        model.addAttribute("sentMode", sentMode);
        model.addAttribute("roleLabel", roleLabel(inboxRole));
        model.addAttribute("viewTitle", sentMode
                ? "Mensajes enviados"
                : selectedSource == currentRole ? roleLabel(currentRole) : senderLabel(selectedSource));
        model.addAttribute("roleDescription", sentMode
                ? "Coordinaciones que enviaste desde tu cuenta."
                : selectedSource == currentRole
                ? roleDescription(inboxRole)
                : "Mensajes enviados por " + senderOrigin(selectedSource) + " a este buzón.");
        model.addAttribute("selectedSource", selectedSource);
        model.addAttribute("sourceOptions", sourceOptions);
        model.addAttribute("messages", messages);
        model.addAttribute("unread", sentMode
                ? 0
                : messages.stream().filter(message -> !message.isReadByTarget()).count());
        return "communications/inbox";
    }

    @GetMapping("/sent")
    public String sent(Authentication authentication, Model model) {
        return "redirect:/communications?box=sent";
    }

    @GetMapping("/new")
    public String newMessage(Authentication authentication, Model model) {
        Role currentRole = currentRole(authentication);
        boolean supportMode = currentRole == Role.CLIENT;

        model.addAttribute("supportMode", supportMode);
        model.addAttribute("senderRole", supportMode ? Role.CLIENT : currentRole);
        model.addAttribute("senderRoleLabel", actorLabel(currentRole));
        model.addAttribute("senderEmail", authentication != null ? authentication.getName() : "");
        model.addAttribute("roles", Role.values());
        model.addAttribute("types", MessageType.values());
        model.addAttribute("priorities", MessagePriority.values());
        model.addAttribute("events", eventService.findAll());
        return "communications/form";
    }

    @PostMapping
    public String create(@RequestParam String senderName,
                         @RequestParam(required = false) String senderEmail,
                         @RequestParam(required = false) Role targetRole,
                         @RequestParam(required = false) MessageType type,
                         @RequestParam(required = false) MessagePriority priority,
                         @RequestParam String subject,
                         @RequestParam String body,
                         @RequestParam(required = false) Long relatedEventId,
                         Authentication authentication) {
        Role currentRole = currentRole(authentication);

        if (currentRole == Role.CLIENT) {
            String email = authentication != null ? authentication.getName() : senderEmail;
            messageService.create(senderName, email, Role.CLIENT, Role.ADMIN, MessageType.CUSTOMER_SUPPORT,
                    MessagePriority.NORMAL, subject, body, null);
            return "redirect:/communications?sent=true";
        }

        Role safeSenderRole = currentRole;
        List<Role> allowedTargets = targetRolesFor(currentRole);
        Role safeTargetRole = targetRole != null && allowedTargets.contains(targetRole)
                ? targetRole : allowedTargets.get(0);
        MessageType safeType = type != null ? type : MessageType.GENERAL_COORDINATION;
        MessagePriority safePriority = priority != null ? priority : MessagePriority.NORMAL;
        String safeSenderEmail = senderEmail == null || senderEmail.isBlank()
                ? authentication != null ? authentication.getName() : "system@connectify.com"
                : senderEmail;

        messageService.create(senderName, safeSenderEmail, safeSenderRole, safeTargetRole, safeType,
                safePriority, subject, body, relatedEventId);
        return "redirect:/communications?box=sent";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        Role currentRole = currentRole(authentication);
        InternalMessage message = currentRole == Role.CLIENT
                ? messageService.findById(id)
                : messageService.markAsRead(id);

        if (currentRole == Role.CLIENT
                && (!message.getSenderEmail().equalsIgnoreCase(authentication.getName())
                || !isCustomerSupport(message))) {
            return "redirect:/communications";
        }

        model.addAttribute("supportMode", currentRole == Role.CLIENT);
        model.addAttribute("currentRole", currentRole);
        model.addAttribute("message", message);
        model.addAttribute("statuses", MessageStatus.values());
        model.addAttribute("senderRoleLabel", actorLabel(message.getSenderRole()));
        model.addAttribute("targetRoleLabel", actorLabel(message.getTargetRole()));
        model.addAttribute("messageTypeLabel", messageTypeLabel(message.getType()));
        model.addAttribute("priorityLabel", switch (message.getPriority()) {
            case LOW -> "Baja";
            case NORMAL -> "Normal";
            case HIGH -> "Alta";
            case URGENT -> "Urgente";
        });
        return "communications/detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam MessageStatus status,
                               Authentication authentication) {
        if (currentRole(authentication) == Role.CLIENT) {
            return "redirect:/communications";
        }
        messageService.updateStatus(id, status);
        return "redirect:/communications/" + id;
    }

    private Role currentRole(Authentication authentication) {
        if (authentication == null) {
            return Role.ADMIN;
        }
        if (hasRole(authentication, "ROLE_DEVELOPER")) return Role.DEVELOPER;
        if (hasRole(authentication, "ROLE_ADMIN")) return Role.ADMIN;
        if (hasRole(authentication, "ROLE_ORGANIZER")) return Role.ORGANIZER;
        if (hasRole(authentication, "ROLE_CLIENT")) return Role.CLIENT;
        if (hasRole(authentication, "ROLE_DESIGNER")) return Role.DESIGNER;
        if (hasRole(authentication, "ROLE_GATE_AGENT")) return Role.GATE_AGENT;
        return Role.ADMIN;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

    private boolean isCustomerSupport(InternalMessage message) {
        return message.getSenderRole() == Role.CLIENT
                && message.getTargetRole() == Role.ADMIN
                && message.getType() == MessageType.CUSTOMER_SUPPORT;
    }

    private List<Role> sourceRolesFor(Role role) {
        return switch (role) {
            case ADMIN -> List.of(Role.ADMIN, Role.ORGANIZER, Role.DESIGNER, Role.CLIENT, Role.DEVELOPER);
            case ORGANIZER -> List.of(Role.ORGANIZER, Role.ADMIN, Role.DESIGNER, Role.DEVELOPER);
            case DESIGNER -> List.of(Role.DESIGNER, Role.ADMIN, Role.ORGANIZER, Role.DEVELOPER);
            case DEVELOPER -> List.of(Role.DEVELOPER, Role.ADMIN, Role.ORGANIZER, Role.DESIGNER);
            default -> List.of(role);
        };
    }

    private List<Role> targetRolesFor(Role role) {
        return switch (role) {
            case ADMIN -> List.of(Role.ORGANIZER, Role.DESIGNER, Role.DEVELOPER);
            case ORGANIZER -> List.of(Role.ADMIN, Role.DESIGNER, Role.DEVELOPER);
            case DESIGNER -> List.of(Role.ADMIN, Role.ORGANIZER, Role.DEVELOPER);
            case DEVELOPER -> List.of(Role.ADMIN, Role.ORGANIZER, Role.DESIGNER);
            default -> List.of(Role.ADMIN);
        };
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Buzón del Administrador";
            case ORGANIZER -> "Buzón del Organizador";
            case DESIGNER -> "Buzón del Diseñador";
            case GATE_AGENT -> "Buzón de Puerta";
            case CLIENT -> "Buzón del Cliente";
            case DEVELOPER -> "Buzón técnico";
        };
    }

    private String senderLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Mensajes del Administrador";
            case ORGANIZER -> "Mensajes del Organizador";
            case DESIGNER -> "Mensajes del Diseñador";
            case GATE_AGENT -> "Mensajes de Puerta";
            case CLIENT -> "Mensajes de Clientes";
            case DEVELOPER -> "Mensajes técnicos";
        };
    }

    private String senderOrigin(Role role) {
        return switch (role) {
            case ADMIN -> "el Administrador";
            case ORGANIZER -> "el Organizador";
            case DESIGNER -> "el Diseñador";
            case GATE_AGENT -> "el equipo de Puerta";
            case CLIENT -> "los Clientes";
            case DEVELOPER -> "el Developer";
        };
    }

    private String roleDescription(Role role) {
        return switch (role) {
            case ADMIN -> "Recibe soporte de clientes y solicitudes internas de revisión.";
            case ORGANIZER -> "Recibe observaciones, aprobaciones y respuestas del Administrador o Diseñador.";
            case DESIGNER -> "Recibe solicitudes visuales y comentarios sobre eventos.";
            case GATE_AGENT -> "Recibe instrucciones para validación QR y control de acceso.";
            case CLIENT -> "Consultas enviadas por el Cliente únicamente a Administración.";
            case DEVELOPER -> "Mensajes técnicos de accesos y permisos.";
        };
    }

    private String actorLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Administrador";
            case ORGANIZER -> "Organizador";
            case DESIGNER -> "Diseñador";
            case GATE_AGENT -> "Equipo de puerta";
            case CLIENT -> "Cliente";
            case DEVELOPER -> "Desarrollo";
        };
    }

    private String messageTypeLabel(MessageType type) {
        return switch (type) {
            case EVENT_REVIEW -> "Revisión de evento";
            case DESIGN_REQUEST -> "Solicitud de diseño";
            case DESIGN_FEEDBACK -> "Comentarios de diseño";
            case GATE_ACCESS -> "Control de acceso";
            case PAYMENT_SUPPORT -> "Consulta de pago";
            case TICKET_SUPPORT -> "Consulta de entradas";
            case CUSTOMER_SUPPORT -> "Atención al cliente";
            case GENERAL_COORDINATION -> "Coordinación general";
        };
    }

    private record MessageSourceOption(Role role, String label) {
    }
}
