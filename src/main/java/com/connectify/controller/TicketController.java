package com.connectify.controller;

import com.connectify.security.GateLoginThrottle;
import com.connectify.service.EventGateAccessService;
import com.connectify.service.EventGateAccessService.GateContext;
import com.connectify.service.EventGateAccessService.GateSession;
import com.connectify.service.TicketService;
import com.connectify.service.TicketService.GateValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

@Controller
@RequestMapping("/tickets")
public class TicketController {
    private final TicketService tickets;
    private final EventGateAccessService gates;
    private final GateLoginThrottle throttle;

    public TicketController(TicketService tickets, EventGateAccessService gates, GateLoginThrottle throttle) {
        this.tickets = tickets;
        this.gates = gates;
        this.throttle = throttle;
    }

    @GetMapping
    public String ticketsHome() { return "redirect:/tickets/validate"; }

    @GetMapping("/validate")
    public String validateForm(HttpSession session, Model model) {
        populate(session, model);
        return "tickets/validate";
    }

    @PostMapping("/validate/open")
    public String openValidation(@RequestParam String eventCode, @RequestParam String gatePassword,
                                 HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        session.removeAttribute(EventGateAccessService.SESSION_KEY);
        String address = request.getRemoteAddr();
        if (!throttle.allows(address)) {
            model.addAttribute("gateError", "Demasiados intentos. Espera 15 minutos antes de volver a probar.");
            populate(session, model);
            return "tickets/validate";
        }
        try {
            GateSession grant = gates.open(eventCode, gatePassword);
            request.changeSessionId();
            session.setAttribute(EventGateAccessService.SESSION_KEY, grant);
            throttle.succeeded(address);
            return "redirect:/tickets/validate";
        } catch (IllegalArgumentException exception) {
            throttle.failed(address);
            model.addAttribute("gateError", exception.getMessage());
            populate(session, model);
            return "tickets/validate";
        }
    }

    @PostMapping("/validate/consult")
    public String consultTicket(@RequestParam String code, HttpSession session, Model model) {
        Optional<GateContext> context = populate(session, model);
        if (context.isEmpty()) return expired(model);
        showResult(model, code, tickets.consultTicketForEvent(code, context.get().event().getId()));
        return "tickets/validate";
    }

    @PostMapping("/validate")
    public String validateTicket(@RequestParam String code, HttpSession session, Model model) {
        Optional<GateContext> context = populate(session, model);
        if (context.isEmpty()) return expired(model);
        GateContext gate = context.get();
        showResult(model, code, tickets.validateTicketForEvent(code, gate.event().getId(), gate.gate().getLabel()));
        return "tickets/validate";
    }

    @PostMapping("/validate/close")
    public String closeGate(HttpSession session, RedirectAttributes redirect) {
        session.removeAttribute(EventGateAccessService.SESSION_KEY);
        redirect.addFlashAttribute("gateNotice", "Acceso de puerta cerrado.");
        return "redirect:/tickets/validate";
    }

    @GetMapping("/gate")
    public String gateRedirect() { return "redirect:/tickets/validate"; }

    private Optional<GateContext> populate(HttpSession session, Model model) {
        model.addAttribute("searched", false);
        model.addAttribute("result", null);
        model.addAttribute("ticket", null);
        model.addAttribute("code", "");
        Object value = session.getAttribute(EventGateAccessService.SESSION_KEY);
        Optional<GateContext> context = gates.resolve(value instanceof GateSession grant ? grant : null);
        model.addAttribute("gateUnlocked", context.isPresent());
        if (context.isPresent()) {
            model.addAttribute("selectedEvent", context.get().event());
            model.addAttribute("gateName", context.get().gate().getLabel());
        } else {
            session.removeAttribute(EventGateAccessService.SESSION_KEY);
        }
        return context;
    }

    private void showResult(Model model, String code, GateValidationResult result) {
        model.addAttribute("searched", true);
        model.addAttribute("code", code);
        model.addAttribute("result", result);
        model.addAttribute("ticket", result.getTicket());
    }

    private String expired(Model model) {
        model.addAttribute("gateError", "Abre el acceso con el código del evento y la contraseña de tu puerta.");
        return "tickets/validate";
    }
}
