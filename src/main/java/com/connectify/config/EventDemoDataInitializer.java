package com.connectify.config;

import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.repository.CategoryRepository;
import com.connectify.repository.EventRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("!prod")
public class EventDemoDataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    public EventDemoDataInitializer(CategoryRepository categoryRepository, EventRepository eventRepository) {
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public void run(String... args) {
        Category music = categoryRepository.findByName("Música")
                .orElseGet(() -> categoryRepository.save(new Category("Música", "🎵", "Conciertos y festivales musicales")));
        Category tech = categoryRepository.findByName("Tecnología")
                .orElseGet(() -> categoryRepository.save(new Category("Tecnología", "💻", "Charlas, conferencias y networking tech")));
        Category food = categoryRepository.findByName("Gastronomía")
                .orElseGet(() -> categoryRepository.save(new Category("Gastronomía", "🍽️", "Ferias, catas y experiencias culinarias")));
        Category sports = categoryRepository.findByName("Deporte")
                .orElseGet(() -> categoryRepository.save(new Category("Deporte", "⚽", "Eventos deportivos y competencias")));

        createIfMissing("Concierto Rock Lima 2026", "Una noche con bandas nacionales, luces y experiencia premium para fans del rock.", music,
                LocalDateTime.now().plusDays(14).withHour(20).withMinute(0), "Arena Costa Verde", "Lima", new BigDecimal("85.00"), 1200, 340, true);
        createIfMissing("Tech Summit Perú", "Conferencia para estudiantes, desarrolladores y empresas sobre IA, cloud y desarrollo web.", tech,
                LocalDateTime.now().plusDays(21).withHour(9).withMinute(30), "Centro de Convenciones de Lima", "Lima", new BigDecimal("120.00"), 800, 210, true);
        createIfMissing("Festival Gastronómico Sabores del Perú", "Stands, chefs invitados, música en vivo y degustaciones de comida peruana.", food,
                LocalDateTime.now().plusDays(10).withHour(12).withMinute(0), "Parque de la Exposición", "Lima", new BigDecimal("35.00"), 2000, 870, false);
        createIfMissing("Final Copa Interbarrios", "Evento deportivo local con tribunas, zona familiar y validación de tickets QR.", sports,
                LocalDateTime.now().plusDays(7).withHour(16).withMinute(0), "Estadio Municipal", "Callao", new BigDecimal("25.00"), 1500, 920, false);

        normalizeDemoEvents();
    }

    private void createIfMissing(String title, String description, Category category, LocalDateTime eventDate,
                                 String location, String city, BigDecimal price, Integer capacity,
                                 Integer sold, boolean featured) {
        boolean exists = eventRepository.findAll().stream().anyMatch(event -> title.equalsIgnoreCase(event.getTitle()));
        if (exists) {
            return;
        }

        eventRepository.save(new Event(title, description, category, eventDate, location, city, price, capacity, sold, "", featured, EventStatus.PUBLISHED));
    }

    private void normalizeDemoEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> events = eventRepository.findAll();
        int offset = 7;

        for (Event event : events) {
            boolean changed = false;

            if (event.getStatus() == null) {
                event.setStatus(EventStatus.PUBLISHED);
                changed = true;
            }

            if (event.getEventDate() == null || event.getEventDate().isBefore(now)) {
                event.setEventDate(now.plusDays(offset).withHour(19).withMinute(30));
                offset += 5;
                changed = true;
            }

            if (event.getPrice() == null) {
                event.setPrice(new BigDecimal("49.00"));
                changed = true;
            }

            if (event.getCapacity() == null || event.getCapacity() <= 0) {
                event.setCapacity(500);
                changed = true;
            }

            if (event.getSold() == null || event.getSold() < 0 || event.getSold() > event.getCapacity()) {
                event.setSold(Math.min(120, event.getCapacity()));
                changed = true;
            }

            if (event.getGateAccessCode() == null || event.getGateAccessCode().isBlank()) {
                event.setGateAccessCode("EVT-" + event.getId());
                changed = true;
            }

            if (changed) {
                eventRepository.save(event);
            }
        }
    }
}
