package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.service.EventService;
import com.connectify.service.EventGateAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/events")
public class AdminEventController {

    private final EventService eventService;
    private final EventGateAccessService gates;

    public AdminEventController(EventService eventService, EventGateAccessService gates) {
        this.eventService = eventService;
        this.gates = gates;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("events", eventService.findAll());
        model.addAttribute("gateCounts", gates.activeCounts());
        return "admin/events";
    }

    @GetMapping("/{id}/gate-access")
    public String gateAccessForm(@PathVariable Long id, Model model) {
        Event event = eventService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        model.addAttribute("event", event);
        model.addAttribute("suggestedEventCode", "EVT-" + id);
        model.addAttribute("gateSlots", gates.slots(id));
        return "admin/gate-access";
    }

    @PostMapping("/{id}/gate-access")
    public String updateGateAccess(@PathVariable Long id,
                                   @RequestParam int slot,
                                   @RequestParam String label,
                                   @RequestParam(defaultValue = "") String gatePassword,
                                   @RequestParam(defaultValue = "false") boolean active,
                                   RedirectAttributes redirect) {
        try {
            gates.saveSlot(id, slot, label, gatePassword, active);
            redirect.addFlashAttribute("gateSuccess", "Puerta " + slot + " guardada. Los accesos anteriores de esa puerta deben abrirse nuevamente.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("gateError", exception.getMessage());
        }
        return "redirect:/admin/events/" + id + "/gate-access";
    }

    @PostMapping("/{id}/gate-code")
    public String updateGateCode(@PathVariable Long id, @RequestParam String gateAccessCode, RedirectAttributes redirect) {
        try {
            gates.updateEventCode(id, gateAccessCode);
            redirect.addFlashAttribute("gateSuccess", "Código del evento guardado.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("gateError", exception.getMessage());
        }
        return "redirect:/admin/events/" + id + "/gate-access";
    }
}
