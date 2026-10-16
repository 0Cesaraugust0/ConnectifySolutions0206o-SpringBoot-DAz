package com.connectify.security;

import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventAdminRecord;
import com.connectify.entity.EventAdminRecordType;
import com.connectify.entity.EventStatus;
import com.connectify.entity.TicketType;
import com.connectify.repository.CategoryRepository;
import com.connectify.repository.EventAdminRecordRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.TicketTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Configuration
public class SecurityConfig {

    @Bean
    public SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider databaseAuthenticationProvider(DatabaseUserDetailsService userDetailsService,
                                                                     PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DaoAuthenticationProvider databaseAuthenticationProvider,
                                                   SessionRegistry sessionRegistry) throws Exception {
        http
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(new AndRequestMatcher(
                        CsrfFilter.DEFAULT_CSRF_MATCHER, new AntPathRequestMatcher("/dashboard/developer/accounts/**"))))
                .sessionManagement(session -> session.maximumSessions(-1)
                        .sessionRegistry(sessionRegistry).expiredUrl("/login?accessUpdated"))
                .authenticationProvider(databaseAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/login", "/register", "/setup", "/prototype",
                                "/terms", "/privacy", "/cookies", "/help", "/help/**",
                                "/events", "/events/**",
                                "/payments/izipay/**",
                                "/css/**", "/js/**", "/images/**", "/uploads/**"
                        ).permitAll()
                        .requestMatchers("/prototype/**").permitAll()
                        .requestMatchers("/dashboard/developer/**").hasRole("DEVELOPER")
                        .requestMatchers("/dashboard/admin/**", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/dashboard/organizer/**").hasRole("ORGANIZER")
                        .requestMatchers("/dashboard/client/**", "/cart/**").hasRole("CLIENT")
                        .requestMatchers("/dashboard/designer/**").hasRole("DESIGNER")
                        .requestMatchers("/dashboard/gate-agent/**", "/tickets/validate/**", "/tickets/gate").hasRole("GATE_AGENT")
                        .requestMatchers("/communications/**").hasAnyRole("DEVELOPER", "ADMIN", "ORGANIZER", "DESIGNER", "CLIENT")
                        .requestMatchers("/dashboard/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard", false)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    @Profile("!prod")
    CommandLineRunner demoEvents(EventRepository events, CategoryRepository categories,
                                 TicketTypeRepository ticketTypes, EventAdminRecordRepository records) {
        return args -> {
            if (events.findAll().stream().anyMatch(event -> "Festival Gastronómico Lima 2026".equals(event.getTitle()))) {
                return;
            }

            Category gastronomy = categories.findByName("Gastronomía")
                    .orElseGet(() -> categories.save(new Category("Gastronomía", "🍲", "Ferias y experiencias culinarias.")));
            Category business = categories.findByName("Negocios")
                    .orElseGet(() -> categories.save(new Category("Negocios", "💼", "Conferencias y encuentros empresariales.")));

            Event festival = saveEvent(events, "Festival Gastronómico Lima 2026", gastronomy,
                    "Valeria Torres", "organizador.altura@connectify.local", EventStatus.PENDING_REVIEW,
                    LocalDateTime.now().plusDays(28), "Parque de la Exposición", "Lima", "45.00", 500);
            ticket(ticketTypes, festival, "General", "Acceso general al festival.", "45.00", 500);
            ticket(ticketTypes, festival, "VIP", "Zona preferente y degustación.", "95.00", 80);
            audit(records, festival, EventAdminRecordType.CREATED, "Evento creado por la organizadora y enviado a revisión administrativa.");
            audit(records, festival, EventAdminRecordType.UPDATED,
                    "Cambios solicitados por organizador:\n\nFecha:\nAntes: 16 jul 2026, 17:00\nDespués: 23 jul 2026, 18:00\n\nCapacidad:\nAntes: 350\nDespués: 500\n\nEntradas:\nAntes: General S/ 40\nDespués: General S/ 45\n\nEvento devuelto a revisión administrativa.");
            audit(records, festival, EventAdminRecordType.TICKET_TYPE_CREATED,
                    "Cambios solicitados por organizador:\n\nEntradas:\nAntes: Sin tipo VIP\nDespués: VIP | S/ 95.00 | Cantidad: 80 | Activo.");

            Event conference = saveEvent(events, "Conferencia Impulsa Negocios", business,
                    "Diego Núñez", "organizador.cultural@connectify.local", EventStatus.PENDING_REVIEW,
                    LocalDateTime.now().plusDays(35), "Centro de Convenciones San Borja", "Lima", "65.00", 280);
            ticket(ticketTypes, conference, "General", "Acceso a conferencias y feria de aliados.", "65.00", 280);
            audit(records, conference, EventAdminRecordType.CREATED, "Evento creado por el organizador y enviado a revisión administrativa.");
            audit(records, conference, EventAdminRecordType.UPDATED,
                    "Cambios solicitados por organizador:\n\nUbicación:\nAntes: Auditorio Centro\nDespués: Centro de Convenciones San Borja\n\nCapacidad:\nAntes: 180\nDespués: 280\n\nEvento devuelto a revisión administrativa.");

            Event approved = saveEvent(events, "Feria de Emprendimiento Juvenil", business,
                    "Valeria Torres", "organizador.altura@connectify.local", EventStatus.APPROVED,
                    LocalDateTime.now().plusDays(42), "Plaza Mayor", "Lima", "15.00", 400);
            ticket(ticketTypes, approved, "General", "Acceso a feria y charlas.", "15.00", 400);
            audit(records, approved, EventAdminRecordType.ADMIN_COPY,
                    "Aprobado: Construcción, datos y entradas conformes. Evento bloqueado para edición.");

            Event published = saveEvent(events, "Concierto Horizonte Andino", gastronomy,
                    "Valeria Torres", "organizador.altura@connectify.local", EventStatus.PUBLISHED,
                    LocalDateTime.now().plusDays(15), "Anfiteatro del Parque de la Reserva", "Lima", "55.00", 600);
            ticket(ticketTypes, published, "General", "Acceso general al concierto.", "55.00", 600);
            audit(records, published, EventAdminRecordType.ADMIN_COPY, "Publicado en marketplace.");
        };
    }

    private Event saveEvent(EventRepository events, String title, Category category, String organizerName,
                            String organizerEmail, EventStatus status, LocalDateTime date,
                            String location, String city, String price, int capacity) {
        Event event = new Event();
        event.setTitle(title);
        event.setCategory(category);
        event.setOrganizerName(organizerName);
        event.setOrganizerEmail(organizerEmail);
        event.setStatus(status);
        event.setEventDate(date);
        event.setLocation(location);
        event.setCity(city);
        event.setDescription("Datos demostrativos para sustentar el flujo de revisión administrativa.");
        event.setPrice(new BigDecimal(price));
        event.setCapacity(capacity);
        event.setSold(0);
        return events.save(event);
    }

    private void ticket(TicketTypeRepository repository, Event event, String name, String description,
                        String price, int quantity) {
        TicketType ticket = new TicketType();
        ticket.setEvent(event);
        ticket.setName(name);
        ticket.setDescription(description);
        ticket.setPrice(new BigDecimal(price));
        ticket.setQuantityAvailable(quantity);
        ticket.setQuantitySold(0);
        ticket.setActive(true);
        repository.save(ticket);
    }

    private void audit(EventAdminRecordRepository repository, Event event, EventAdminRecordType type,
                       String description) {
        EventAdminRecord record = new EventAdminRecord();
        record.setEvent(event);
        record.setType(type);
        record.setDescription(description);
        repository.save(record);
    }
}
