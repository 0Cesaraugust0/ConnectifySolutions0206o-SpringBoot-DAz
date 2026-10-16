package com.connectify.service;

import com.connectify.entity.*;
import com.connectify.repository.AccountAccessAuditRepository;
import com.connectify.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DeveloperAccountService {
    public static final Set<Role> MANAGED_ROLES = Set.of(Role.ADMIN, Role.ORGANIZER, Role.DESIGNER, Role.CLIENT, Role.GATE_AGENT);
    private final UserAccountRepository users;
    private final AccountAccessAuditRepository audits;
    private final PasswordEncoder encoder;
    private final EntityManager entityManager;
    private final SessionRegistry sessions;

    public DeveloperAccountService(UserAccountRepository users, AccountAccessAuditRepository audits,
                                   PasswordEncoder encoder, EntityManager entityManager, SessionRegistry sessions) {
        this.users = users; this.audits = audits; this.encoder = encoder;
        this.entityManager = entityManager; this.sessions = sessions;
    }

    public record AccountInput(String firstName, String paternalLastName, String maternalLastName,
                               String email, String dni, String phone, String companyName, String ruc, Role role) { }
    public record AccountRow(UserAccount account, List<String> missingFields) {
        public boolean isComplete() { return missingFields.isEmpty(); }
    }
    public record RegistrationCount(String label, Role role, long total, long active, long complete,
                                    long incomplete, long newToday, long newLast30Days, long newCompleteLast30Days,
                                    long unknownRegistrationDate) { }

    @Transactional(readOnly = true)
    public List<AccountRow> accounts() {
        return users.findAll().stream().filter(u -> MANAGED_ROLES.contains(u.getRole()))
                .sorted(Comparator.comparing(UserAccount::getRole).thenComparing(UserAccount::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new AccountRow(u, AccountProfilePolicy.missingFields(u))).toList();
    }

    public List<RegistrationCount> counters(List<AccountRow> rows) {
        Instant now = Instant.now();
        Instant since = now.minus(30, ChronoUnit.DAYS);
        Instant today = now.atZone(ZoneId.of("America/Lima")).toLocalDate().atStartOfDay(ZoneId.of("America/Lima")).toInstant();
        return List.of(counter(rows, Role.CLIENT, "Clientes", today, since), counter(rows, Role.ORGANIZER, "Organizadores", today, since));
    }

    private RegistrationCount counter(List<AccountRow> rows, Role role, String label, Instant today, Instant since) {
        List<AccountRow> selected = rows.stream().filter(r -> r.account().getRole() == role).toList();
        long complete = selected.stream().filter(AccountRow::isComplete).count();
        return new RegistrationCount(label, role, selected.size(), selected.stream().filter(r -> r.account().isActive()).count(),
                complete, selected.size() - complete,
                selected.stream().filter(r -> registeredAfter(r.account(), today)).count(),
                selected.stream().filter(r -> registeredAfter(r.account(), since)).count(),
                selected.stream().filter(AccountRow::isComplete).filter(r -> registeredAfter(r.account(), since)).count(),
                selected.stream().filter(r -> r.account().getCreatedAt() == null).count());
    }

    private boolean registeredAfter(UserAccount u, Instant since) { return u.getCreatedAt() != null && !u.getCreatedAt().isBefore(since); }

    @Transactional(readOnly = true)
    public UserAccount managed(Long id, String actor) {
        UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
        return checkManaged(u, actor);
    }

    private UserAccount managedForUpdate(Long id, String actor) {
        return checkManaged(users.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada.")), actor);
    }

    private UserAccount checkManaged(UserAccount u, String actor) {
        if (!MANAGED_ROLES.contains(u.getRole()) || u.getEmail().equalsIgnoreCase(actor))
            throw new AccessDeniedException("Esta cuenta de desarrollador está protegida.");
        return u;
    }

    @Transactional
    public void create(AccountInput input, String password, String actor) {
        validate(input);
        validatePassword(password);
        String email = clean(input.email()).toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("Ya existe una cuenta con este correo.");
        UserAccount u = new UserAccount();
        apply(u, input);
        u.setCredentialHash(encoder.encode(password));
        u.setActive(true);
        users.saveAndFlush(u);
        audit(actor, u, "CREAR", "Cuenta creada con rol " + u.getRole().name());
    }

    @Transactional
    public void update(Long id, AccountInput input, String actor) {
        validate(input);
        UserAccount u = managedForUpdate(id, actor);
        String oldEmail = u.getEmail();
        String email = clean(input.email()).toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("Ya existe otra cuenta con este correo.");
        // Switching a user's role can hide its existing event or purchase history.
        if (input.role() != u.getRole()) throw new IllegalArgumentException("El rol se conserva para mantener el historial de esta cuenta.");
        if (!oldEmail.equals(email) && entityManager.createQuery(
                "select count(a) from IzipayWebAttempt a where lower(a.buyerEmail) = lower(:email) and a.status in ('PENDING', 'REVIEW')", Long.class)
                .setParameter("email", oldEmail).getSingleResult() > 0)
            throw new IllegalArgumentException("La cuenta tiene un pago pendiente o en revisión. Su correo se podrá cambiar cuando se resuelva, para preservar la confirmación de IZIPay.");
        apply(u, input);
        users.saveAndFlush(u);
        if (!oldEmail.equals(email)) migrateEmail(oldEmail, email);
        audit(actor, u, "EDITAR", "Datos de perfil y correo de acceso actualizados.");
        expireAfterCommit(oldEmail);
    }

    @Transactional
    public void resetPassword(Long id, String password, String confirmation, String actor) {
        UserAccount u = managedForUpdate(id, actor);
        validatePassword(password);
        if (!Objects.equals(password, confirmation)) throw new IllegalArgumentException("Las contraseñas no coinciden.");
        u.setCredentialHash(encoder.encode(password));
        users.save(u);
        audit(actor, u, "CONTRASEÑA", "Contraseña restablecida; sesiones anteriores cerradas.");
        expireAfterCommit(u.getEmail());
    }

    @Transactional
    public void setActive(Long id, boolean active, String actor) {
        UserAccount u = managedForUpdate(id, actor);
        u.setActive(active);
        users.save(u);
        audit(actor, u, active ? "ACTIVAR" : "DESACTIVAR", active ? "Acceso habilitado." : "Acceso suspendido; historial conservado.");
        expireAfterCommit(u.getEmail());
    }

    @Transactional
    public void delete(Long id, String actor) {
        UserAccount u = managedForUpdate(id, actor);
        String email = u.getEmail();
        // Never cascade through tickets, orders, payments or published events.
        List<String> checks = List.of(
                "select count(e) from Event e where lower(e.organizerEmail) = lower(:email)",
                "select count(p) from Purchase p where lower(p.buyerEmail) = lower(:email)",
                "select count(t) from Ticket t where lower(t.attendeeEmail) = lower(:email)",
                "select count(a) from IzipayWebAttempt a where lower(a.buyerEmail) = lower(:email)",
                "select count(m) from InternalMessage m where lower(m.senderEmail) = lower(:email)",
                "select count(c) from ShoppingCartConsumption c where lower(c.buyerEmail) = lower(:email)",
                "select count(c) from ShoppingCart c where lower(c.buyer.email) = lower(:email)",
                "select count(f) from ClientFavorite f where lower(f.client.email) = lower(:email)");
        for (String query : checks) {
            if (entityManager.createQuery(query, Long.class).setParameter("email", email).getSingleResult() > 0)
                throw new IllegalArgumentException("Esta cuenta tiene actividad asociada. Usa Desactivar para depurar su acceso y conservar su historial.");
        }
        audit(actor, u, "ELIMINAR", "Cuenta sin actividad eliminada.");
        users.delete(u);
        users.flush();
        expireAfterCommit(email);
    }

    private void migrateEmail(String oldEmail, String email) {
        for (String field : List.of("Event:organizerEmail", "Purchase:buyerEmail", "Ticket:attendeeEmail",
                "IzipayWebAttempt:buyerEmail", "InternalMessage:senderEmail", "ShoppingCartConsumption:buyerEmail")) {
            String[] parts = field.split(":");
            entityManager.createQuery("update " + parts[0] + " x set x." + parts[1] + " = :email where lower(x." + parts[1] + ") = lower(:oldEmail)")
                    .setParameter("email", email).setParameter("oldEmail", oldEmail).executeUpdate();
        }
    }

    private void expireAfterCommit(String email) {
        Runnable expire = () -> sessions.getAllPrincipals().stream()
                .filter(p -> p instanceof UserDetails d && d.getUsername().equalsIgnoreCase(email))
                .forEach(p -> sessions.getAllSessions(p, false).forEach(s -> s.expireNow()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { expire.run(); }
            });
        } else expire.run();
    }

    private void apply(UserAccount u, AccountInput i) {
        u.setFirstName(clean(i.firstName())); u.setPaternalLastName(clean(i.paternalLastName()));
        u.setMaternalLastName(clean(i.maternalLastName()));
        u.setFullName((u.getFirstName() + " " + u.getPaternalLastName() + " " + u.getMaternalLastName()).trim().replaceAll("\\s+", " "));
        u.setEmail(clean(i.email()).toLowerCase(Locale.ROOT)); u.setDni(clean(i.dni())); u.setPhone(clean(i.phone()));
        u.setCompanyName(clean(i.companyName())); u.setRuc(clean(i.ruc())); u.setRole(i.role());
    }

    private void validate(AccountInput i) {
        if (i.role() == null || !MANAGED_ROLES.contains(i.role())) throw new IllegalArgumentException("Selecciona un rol administrable.");
        if (clean(i.firstName()).length() < 2 || clean(i.paternalLastName()).length() < 2) throw new IllegalArgumentException("Completa nombres y apellido paterno.");
        if (!clean(i.email()).matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Ingresa un correo válido.");
        if (!clean(i.dni()).isEmpty() && !clean(i.dni()).matches("\\d{8}")) throw new IllegalArgumentException("El DNI debe tener 8 dígitos.");
        if (!clean(i.ruc()).isEmpty() && !clean(i.ruc()).matches("\\d{11}")) throw new IllegalArgumentException("El RUC debe tener 11 dígitos.");
        if (!clean(i.phone()).isEmpty() && !clean(i.phone()).replaceAll("[\\s()+-]", "").matches("\\d{9,15}")) throw new IllegalArgumentException("Ingresa un teléfono válido (9 a 15 dígitos).");
        for (String value : List.of(clean(i.firstName()), clean(i.paternalLastName()), clean(i.maternalLastName()), clean(i.email()), clean(i.companyName())))
            if (value.length() > 200) throw new IllegalArgumentException("Los campos deben tener como máximo 200 caracteres.");
        if ((clean(i.firstName()) + " " + clean(i.paternalLastName()) + " " + clean(i.maternalLastName())).length() > 250)
            throw new IllegalArgumentException("El nombre completo debe tener como máximo 250 caracteres.");
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Usa una contraseña de al menos 12 caracteres y como máximo 72 bytes.");
    }

    private void audit(String actor, UserAccount u, String action, String detail) {
        audits.save(new AccountAccessAudit(actor, u.getEmail(), action, detail));
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
