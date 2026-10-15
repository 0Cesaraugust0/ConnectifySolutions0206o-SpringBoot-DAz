package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.TicketType;
import com.connectify.repository.CategoryRepository;
import com.connectify.repository.EventAdminRecordRepository;
import com.connectify.repository.EventRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.InternalMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizerDashboardControllerMetricsTest {

    @Test
    void metricsBreaksRevenueDownByTicketTypeAndKeepsLegacySalesVisible() {
        EventRepository events = mock(EventRepository.class);
        TicketTypeRepository ticketTypes = mock(TicketTypeRepository.class);
        OrganizerDashboardController controller = new OrganizerDashboardController(
                events,
                mock(CategoryRepository.class),
                ticketTypes,
                mock(EventAdminRecordRepository.class),
                mock(InternalMessageService.class),
                mock(UserAccountRepository.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("organizador@connectify.local");

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(7L);
        when(event.getPrice()).thenReturn(new BigDecimal("25.00"));
        when(event.getCapacity()).thenReturn(100);
        when(event.getSold()).thenReturn(4);

        TicketType vip = new TicketType();
        vip.setEvent(event);
        vip.setName("VIP");
        vip.setPrice(new BigDecimal("60.00"));
        vip.setQuantityAvailable(20);
        vip.setQuantitySold(3);
        vip.setActive(true);

        when(events.findByOrganizerEmailIgnoreCaseOrderByUpdatedAtDesc("organizador@connectify.local"))
                .thenReturn(List.of(event));
        when(ticketTypes.findByEventIdInOrderByEventIdAscPriceAsc(List.of(7L))).thenReturn(List.of(vip));

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.metrics(model, authentication);

        assertThat(view).isEqualTo("dashboard/organizer/metrics");
        assertThat(model.getAttribute("revenue")).isEqualTo(new BigDecimal("205.00"));
        List<?> rows = (List<?>) model.getAttribute("eventMetrics");
        assertThat(rows).hasSize(1);
        OrganizerDashboardController.EventMetric row = (OrganizerDashboardController.EventMetric) rows.getFirst();
        assertThat(row.getTicketTypes()).extracting(OrganizerDashboardController.TicketMetric::getName)
                .containsExactly("VIP", "Ventas sin tipo asociado");
        assertThat(row.getRevenue()).isEqualTo(new BigDecimal("205.00"));
    }
}
