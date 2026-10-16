package com.connectify.service;

import com.connectify.entity.*;
import com.connectify.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeveloperAccountServiceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AccountAccessAuditRepository audits = mock(AccountAccessAuditRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final DeveloperAccountService service = new DeveloperAccountService(users, audits, encoder, em, sessions);
    private final String actor = "dev@connectify.local";

    private UserAccount client() {
        UserAccount u = new UserAccount("Lucía Pérez", "lucia@example.test", encoder.encode("Original-Test-2026!"), Role.CLIENT);
        u.setFirstName("Lucía"); u.setPaternalLastName("Pérez"); u.setDni("12345678"); u.setPhone("987654321");
        ReflectionTestUtils.setField(u, "id", 12L);
        when(users.findById(12L)).thenReturn(Optional.of(u));
        when(users.findLockedById(12L)).thenReturn(Optional.of(u));
        return u;
    }

    private DeveloperAccountService.AccountInput input(String email, Role role) {
        return new DeveloperAccountService.AccountInput("Lucía", "Pérez", "", email, "12345678", "987654321", "", "", role);
    }

    @Test void createsClientsWithHashedPasswordsAndNoPasswordInAudit() {
        String password = "A-Strong-Test-2026!";
        service.create(input(" LUCIA@example.test ", Role.CLIENT), password, actor);
        verify(users).saveAndFlush(argThat(u -> u.getEmail().equals("lucia@example.test") && u.isActive()
                && encoder.matches(password, u.getCredentialHash()) && !u.getCredentialHash().equals(password)));
        verify(audits).save(argThat(a -> a.getAction().equals("CREAR") && !a.getDetail().contains(password)));
    }

    @Test void refusesDuplicateEmailsAndDeveloperProvisioning() {
        when(users.existsByEmailIgnoreCase("lucia@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.create(input("lucia@example.test", Role.CLIENT), "Long-Password-2026!", actor)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(input("new@example.test", Role.DEVELOPER), "Long-Password-2026!", actor)).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test void protectsDeveloperAccountsAndSelf() {
        UserAccount u = client(); u.setRole(Role.DEVELOPER);
        assertThatThrownBy(() -> service.delete(12L, actor)).isInstanceOf(AccessDeniedException.class);
        u.setRole(Role.CLIENT);
        assertThatThrownBy(() -> service.setActive(12L, false, u.getEmail())).isInstanceOf(AccessDeniedException.class);
        verify(users, never()).delete(any());
    }

    @Test void resetsPasswordAndExpiresExistingSessions() {
        UserAccount u = client();
        var principal = User.withUsername(u.getEmail()).password("unused").roles("CLIENT").build();
        SessionInformation session = new SessionInformation(principal, "session-1", new java.util.Date());
        when(sessions.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessions.getAllSessions(principal, false)).thenReturn(List.of(session));
        service.resetPassword(12L, "Replacement-2026!", "Replacement-2026!", actor);
        assertThat(encoder.matches("Replacement-2026!", u.getCredentialHash())).isTrue();
        assertThat(session.isExpired()).isTrue();
        verify(audits).save(argThat(a -> a.getAction().equals("CONTRASEÑA")));
    }

    @Test void rejectsMismatchedPasswordWithoutSaving() {
        client();
        assertThatThrownBy(() -> service.resetPassword(12L, "Replacement-2026!", "Different-2026!", actor)).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test void deactivatesWithoutDeletingHistory() {
        UserAccount u = client(); when(sessions.getAllPrincipals()).thenReturn(List.of());
        service.setActive(12L, false, actor);
        assertThat(u.isActive()).isFalse();
        verify(users, never()).delete(any());
        verify(em, never()).createQuery(anyString());
    }

    @SuppressWarnings("unchecked")
    @Test void blocksDeletionWhenCommercialActivityExists() {
        client(); TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("email"), anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(12L, actor)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Desactivar");
        verify(users, never()).delete(any());
    }

    @SuppressWarnings("unchecked")
    @Test void deletesUnusedAccountAfterCheckingAllLinks() {
        UserAccount u = client(); TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("email"), anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        when(sessions.getAllPrincipals()).thenReturn(List.of());
        service.delete(12L, actor);
        verify(em, times(8)).createQuery(anyString(), eq(Long.class));
        verify(users).delete(u);
        verify(audits).save(argThat(a -> a.getAction().equals("ELIMINAR")));
    }

    @SuppressWarnings("unchecked")
    @Test void emailChangePreservesAllHistoryReferences() {
        UserAccount u = client(); TypedQuery<Long> pending = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(pending);
        when(pending.setParameter(anyString(), any())).thenReturn(pending); when(pending.getSingleResult()).thenReturn(0L);
        Query update = mock(Query.class); when(em.createQuery(anyString())).thenReturn(update);
        when(update.setParameter(anyString(), any())).thenReturn(update); when(sessions.getAllPrincipals()).thenReturn(List.of());
        service.update(12L, input("new@example.test", Role.CLIENT), actor);
        assertThat(u.getEmail()).isEqualTo("new@example.test");
        verify(em, times(6)).createQuery(startsWith("update "));
        verify(update, times(6)).setParameter("oldEmail", "lucia@example.test");
    }

    @SuppressWarnings("unchecked")
    @Test void preservesGatewayConfirmationIdentityDuringPendingPayments() {
        UserAccount u = client(); TypedQuery<Long> pending = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(pending);
        when(pending.setParameter(anyString(), any())).thenReturn(pending); when(pending.getSingleResult()).thenReturn(1L);
        assertThatThrownBy(() -> service.update(12L, input("new@example.test", Role.CLIENT), actor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IZIPay");
        assertThat(u.getEmail()).isEqualTo("lucia@example.test");
        verify(users, never()).saveAndFlush(any());
    }

    @Test void completenessRequiresValidIdentityAndContactAndOrganizerBusiness() {
        UserAccount u = client(); assertThat(AccountProfilePolicy.missingFields(u)).isEmpty();
        u.setRole(Role.ORGANIZER);
        assertThat(AccountProfilePolicy.missingFields(u)).containsExactly("Nombre comercial / razón social", "RUC de 11 dígitos");
        u.setCompanyName("Cultura Lima"); u.setRuc("20123456789");
        assertThat(AccountProfilePolicy.missingFields(u)).isEmpty();
        u.setPhone("123"); assertThat(AccountProfilePolicy.missingFields(u)).containsExactly("Teléfono");
    }

    @Test void unknownLegacyDatesAreNotCountedAsNewAndNewCompleteCountsAreDistinct() {
        UserAccount legacy = client();
        UserAccount recent = new UserAccount("Nuevo", "nuevo@example.test", "hash", Role.CLIENT);
        ReflectionTestUtils.setField(recent, "createdAt", Instant.now());
        List<DeveloperAccountService.AccountRow> rows = List.of(new DeveloperAccountService.AccountRow(legacy, List.of()),
                new DeveloperAccountService.AccountRow(recent, List.of("Teléfono")));
        var clients = service.counters(rows).getFirst();
        assertThat(clients.total()).isEqualTo(2); assertThat(clients.complete()).isEqualTo(1);
        assertThat(clients.newLast30Days()).isEqualTo(1); assertThat(clients.newCompleteLast30Days()).isZero();
        assertThat(clients.unknownRegistrationDate()).isEqualTo(1);
    }
}
