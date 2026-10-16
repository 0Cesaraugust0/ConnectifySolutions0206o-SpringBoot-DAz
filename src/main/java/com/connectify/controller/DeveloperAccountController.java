package com.connectify.controller;

import com.connectify.entity.Role;
import com.connectify.entity.UserAccount;
import com.connectify.repository.AccountAccessAuditRepository;
import com.connectify.service.DeveloperAccountService;
import com.connectify.service.DeveloperAccountService.AccountInput;
import com.connectify.service.DeveloperAccountService.AccountRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/dashboard/developer/accounts")
public class DeveloperAccountController {
    private final DeveloperAccountService service;
    private final AccountAccessAuditRepository audits;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

    public DeveloperAccountController(DeveloperAccountService service, AccountAccessAuditRepository audits) {
        this.service = service; this.audits = audits;
    }

    @GetMapping
    public String accounts(@RequestParam(required = false) Long edit,
                           @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "") String role,
                           @RequestParam(defaultValue = "") String status,
                           @RequestParam(defaultValue = "") String profile,
                           Model model, Authentication authentication) {
        UserAccount editing = edit == null ? null : service.managed(edit, authentication.getName());
        AccountInput form = editing == null ? new AccountInput("", "", "", "", "", "", "", "", Role.CLIENT)
                : new AccountInput(editing.getFirstName(), editing.getPaternalLastName(), editing.getMaternalLastName(),
                editing.getEmail(), editing.getDni(), editing.getPhone(), editing.getCompanyName(), editing.getRuc(), editing.getRole());
        return show(model, authentication, form, editing, q, role, status, profile);
    }

    @PostMapping
    public String create(@ModelAttribute("form") AccountInput form, @RequestParam String password,
                         Model model, Authentication authentication, RedirectAttributes flash) {
        try {
            service.create(form, password, authentication.getName());
            flash.addFlashAttribute("success", "Cuenta creada correctamente.");
            return "redirect:/dashboard/developer/accounts";
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            model.addAttribute("error", message(ex));
            return show(model, authentication, form, null, "", "", "", "");
        }
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @ModelAttribute("form") AccountInput form,
                         Model model, Authentication authentication, RedirectAttributes flash) {
        try {
            service.update(id, form, authentication.getName());
            flash.addFlashAttribute("success", "Perfil y correo de acceso actualizados. Las sesiones anteriores se cerrarán.");
            return "redirect:/dashboard/developer/accounts";
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            model.addAttribute("error", message(ex));
            return show(model, authentication, form, service.managed(id, authentication.getName()), "", "", "", "");
        }
    }

    @PostMapping("/{id}/password")
    public String password(@PathVariable Long id, @RequestParam String password, @RequestParam String confirmation,
                           Authentication authentication, RedirectAttributes flash) {
        try {
            service.resetPassword(id, password, confirmation, authentication.getName());
            flash.addFlashAttribute("success", "Contraseña actualizada. Se cerrarán las sesiones anteriores de esta cuenta.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/dashboard/developer/accounts?edit=" + id;
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam boolean active, Authentication authentication, RedirectAttributes flash) {
        service.setActive(id, active, authentication.getName());
        flash.addFlashAttribute("success", active ? "Cuenta activada." : "Cuenta desactivada. Su historial se conserva.");
        return "redirect:/dashboard/developer/accounts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        try {
            service.delete(id, authentication.getName());
            flash.addFlashAttribute("success", "Cuenta sin actividad eliminada.");
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) { flash.addFlashAttribute("error", message(ex)); }
        return "redirect:/dashboard/developer/accounts";
    }

    private String show(Model model, Authentication authentication, AccountInput form, UserAccount editing,
                        String q, String role, String status, String profile) {
        List<AccountRow> all = service.accounts();
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<AccountRow> selected = all.stream()
                .filter(r -> needle.isBlank() || (r.account().getFullName() + " " + r.account().getEmail() + " " + r.account().getDni()).toLowerCase(Locale.ROOT).contains(needle))
                .filter(r -> role.isBlank() || r.account().getRole().name().equals(role))
                .filter(r -> status.isBlank() || ("active".equals(status) == r.account().isActive()))
                .filter(r -> profile.isBlank() || ("complete".equals(profile) == r.isComplete())).toList();
        model.addAttribute("rows", selected);
        model.addAttribute("accountCount", all.size());
        model.addAttribute("counters", service.counters(all));
        model.addAttribute("audits", audits.findTop30ByOrderByCreatedAtDesc());
        model.addAttribute("dateFormat", DATE);
        model.addAttribute("form", form);
        model.addAttribute("editing", editing);
        model.addAttribute("q", q); model.addAttribute("roleFilter", role);
        model.addAttribute("statusFilter", status); model.addAttribute("profileFilter", profile);
        model.addAttribute("developerEmail", authentication.getName());
        return "dashboard/developer/accounts";
    }

    private String message(RuntimeException ex) {
        return ex instanceof DataIntegrityViolationException
                ? "No se pudo guardar: existe un correo duplicado o actividad vinculada a esta cuenta." : ex.getMessage();
    }
}
