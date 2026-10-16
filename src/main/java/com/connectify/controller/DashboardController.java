package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.EventDesignTemplate;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.entity.UserAccount;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
public class DashboardController {

    private final EventRepository eventRepository;
    private final EventPresentationSettingsRepository presentationSettingsRepository;
    private final UserAccountRepository userAccountRepository;

    public DashboardController(EventRepository eventRepository,
                               EventPresentationSettingsRepository presentationSettingsRepository,
                               UserAccountRepository userAccountRepository) {
        this.eventRepository = eventRepository;
        this.presentationSettingsRepository = presentationSettingsRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        boolean developer = hasRole(authentication, "ROLE_DEVELOPER");
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        boolean organizer = hasRole(authentication, "ROLE_ORGANIZER");
        boolean client = hasRole(authentication, "ROLE_CLIENT");
        boolean designer = hasRole(authentication, "ROLE_DESIGNER");
        boolean gateAgent = hasRole(authentication, "ROLE_GATE_AGENT");

        if (developer) return "redirect:/dashboard/developer";
        if (admin) return "redirect:/dashboard/admin";
        if (organizer) return "redirect:/dashboard/organizer";
        if (client) return "redirect:/dashboard/client";
        if (designer) return "redirect:/dashboard/designer";
        if (gateAgent) return "redirect:/dashboard/gate-agent";
        return "redirect:/";
    }

    @GetMapping("/dashboard/developer")
    public String developer(Model model, Authentication authentication) {
        model.addAttribute("title", "Panel Developer");
        model.addAttribute("email", authentication.getName());
        return "redirect:/dashboard/developer/accounts";
    }

    @GetMapping("/dashboard/admin")
    public String admin(Model model, Authentication authentication) {
        model.addAttribute("title", "Panel Administrador");
        model.addAttribute("email", authentication.getName());
        return "dashboard/admin";
    }

    @GetMapping("/dashboard/organizer")
    public String organizer(Model model, Authentication authentication) {
        UserAccount account = currentAccount(authentication);
        model.addAttribute("title", "Panel Organizador");
        model.addAttribute("email", authentication.getName());
        model.addAttribute("account", account);
        model.addAttribute("organizerName", text(account == null ? null : account.getCompanyName(), "Datos pendientes"));
        model.addAttribute("organizerRuc", text(account == null ? null : account.getRuc(), "Sin RUC"));
        model.addAttribute("organizerProfileComplete", isOrganizerProfileComplete(account));
        return "dashboard/organizer";
    }

    @GetMapping("/dashboard/organizer/profile")
    public String organizerProfile(Model model, Authentication authentication) {
        model.addAttribute("account", currentAccount(authentication));
        return "dashboard/organizer/profile";
    }

    @PostMapping("/dashboard/organizer/profile")
    public String updateOrganizerProfile(@RequestParam String firstName,
                                         @RequestParam String paternalLastName,
                                         @RequestParam(required = false) String maternalLastName,
                                         @RequestParam String companyName,
                                         @RequestParam String ruc,
                                         @RequestParam(required = false) String dni,
                                         @RequestParam(required = false) String phone,
                                         Authentication authentication) {
        UserAccount account = currentAccount(authentication);
        if (account == null) {
            return "redirect:/dashboard/organizer/profile?error=true";
        }

        String cleanFirstName = clean(firstName);
        String cleanPaternalLastName = clean(paternalLastName);
        String cleanMaternalLastName = clean(maternalLastName);
        account.setFirstName(cleanFirstName);
        account.setPaternalLastName(cleanPaternalLastName);
        account.setMaternalLastName(cleanMaternalLastName);
        account.setFullName((cleanFirstName + " " + cleanPaternalLastName + " " + cleanMaternalLastName).trim().replaceAll("\\s+", " "));
        account.setCompanyName(clean(companyName));
        account.setRuc(clean(ruc));
        account.setDni(clean(dni));
        account.setPhone(clean(phone));
        userAccountRepository.save(account);
        return "redirect:/dashboard/organizer?profileSaved=true";
    }

    @GetMapping("/dashboard/client")
    public String client(Model model, Authentication authentication) {
        model.addAttribute("title", "Mi cuenta Cliente");
        model.addAttribute("email", authentication.getName());
        return "dashboard/client";
    }

    @GetMapping("/dashboard/designer")
    public String designer(@RequestParam(required = false) Long eventId, Model model, Authentication authentication) {
        List<Event> designEvents = eventRepository.findAll().stream()
                .filter(Event::isDesignEnabled)
                .filter(event -> event.getStatus() != EventStatus.APPROVED
                        && event.getStatus() != EventStatus.PUBLISHED
                        && event.getStatus() != EventStatus.REJECTED
                        && event.getStatus() != EventStatus.CANCELLED)
                .sorted(Comparator.comparing(Event::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Event selectedEvent = designEvents.stream()
                .filter(event -> eventId != null && eventId.equals(event.getId()))
                .findFirst()
                .orElse(designEvents.isEmpty() ? null : designEvents.get(0));

        EventPresentationSettings presentation = selectedEvent == null ? null
                : presentationSettingsRepository.findByEventId(selectedEvent.getId()).orElse(null);

        model.addAttribute("title", "Estudio de Presentación");
        model.addAttribute("email", authentication.getName());
        model.addAttribute("designEvents", designEvents);
        model.addAttribute("selectedEvent", selectedEvent);
        model.addAttribute("presentation", presentation);
        model.addAttribute("templates", EventDesignTemplate.values());
        return "dashboard/designer-studio";
    }

    @GetMapping("/dashboard/gate-agent")
    public String gateAgent(Model model, Authentication authentication) {
        model.addAttribute("title", "Panel Auxiliar de Puerta");
        model.addAttribute("email", authentication.getName());
        return "dashboard/gate-agent";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

    private UserAccount currentAccount(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userAccountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private boolean isOrganizerProfileComplete(UserAccount account) {
        return hasText(account == null ? null : account.getCompanyName())
                && hasText(account == null ? null : account.getRuc())
                && hasText(account == null ? null : account.getFirstName())
                && hasText(account == null ? null : account.getPaternalLastName());
    }

    private String text(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
