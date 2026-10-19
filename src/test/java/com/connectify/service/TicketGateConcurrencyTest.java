package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.entity.Purchase;
import com.connectify.entity.PurchaseStatus;
import com.connectify.entity.Ticket;
import com.connectify.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:gate-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;NON_KEYWORDS=AUTHORIZATION",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TicketService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketGateConcurrencyTest {
    @Autowired private TicketService service;
    @Autowired private TicketRepository tickets;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactions;

    @Test void twoDoorsScanningOneQrConcurrentlyAllowExactlyOneAdmission() throws Exception {
        Long eventId = new TransactionTemplate(transactions).execute(status -> {
            Event event = new Event();
            event.setTitle("Evento de prueba de concurrencia");
            event.setStatus(EventStatus.PUBLISHED);
            entityManager.persist(event);
            Purchase purchase = new Purchase();
            purchase.setStatus(PurchaseStatus.COMPLETED);
            entityManager.persist(purchase);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setPurchase(purchase);
            ticket.setCode("QR-CONCURRENT-TEST");
            ticket.setAttendeeName("Asistente de prueba");
            ticket.setAttendeeEmail("asistente@example.test");
            entityManager.persist(ticket);
            entityManager.flush();
            return event.getId();
        });
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> north = admission(eventId, "Puerta norte", ready, start);
        Callable<String> south = admission(eventId, "Puerta sur", ready, start);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(north);
            var second = executor.submit(south);
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("VALID", "USED");
        }

        Ticket recorded = tickets.findByCode("QR-CONCURRENT-TEST").orElseThrow();
        assertThat(recorded.isUsed()).isTrue();
        assertThat(recorded.getUsedAt()).isNotNull();
        assertThat(recorded.getUsedGateName()).isIn("Puerta norte", "Puerta sur");
    }

    private Callable<String> admission(Long eventId, String name, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No se sincronizaron las dos puertas de prueba.");
            return service.validateTicketForEvent("QR-CONCURRENT-TEST", eventId, name).getStatus();
        };
    }
}
