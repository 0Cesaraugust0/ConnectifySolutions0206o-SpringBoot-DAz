package com.connectify.controller;

import com.connectify.entity.*;
import com.connectify.security.DatabaseUserDetailsService;
import com.connectify.security.GateSecurityConfig;
import com.connectify.security.SecurityConfig;
import com.connectify.service.EventGateAccessService;
import com.connectify.service.EventService;
import com.connectify.service.InternalMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=CommunicationController.class, properties={
        "spring.config.location=optional:classpath:/admin-preview-mvc.properties", "spring.config.import="})
@ContextConfiguration(classes={CommunicationController.class, GateSecurityConfig.class, SecurityConfig.class})
@ActiveProfiles("prod")
class CommunicationDetailTemplateTest {
    @Autowired MockMvc mvc;
    @MockBean InternalMessageService messages;
    @MockBean EventService events;
    @MockBean EventGateAccessService gateAccess;
    @MockBean DatabaseUserDetailsService users;
    private InternalMessage message;

    @BeforeEach
    void fixture() {
        message = new InternalMessage();
        ReflectionTestUtils.setField(message, "id", 1L);
        message.setSenderRole(Role.ADMIN);
        message.setTargetRole(Role.ORGANIZER);
        message.setSenderName("Administración de Connectify");
        message.setSenderEmail("administracion@example.test");
        message.setType(MessageType.EVENT_REVIEW);
        message.setPriority(MessagePriority.NORMAL);
        message.setStatus(MessageStatus.OBSERVED);
        message.setReadByTarget(true);
        message.setSubject("Observaciones para la publicación de tu evento");
        message.setBody("Hola, Andrea:\n\nHemos revisado la propuesta de tu evento. Para continuar con la publicación, completa la descripción de las zonas de acceso y confirma los cupos de cada tipo de entrada.\n\nLa portada y el programa están correctamente presentados. Cuando guardes los ajustes, puedes enviar nuevamente la propuesta a revisión.\n\nGracias por ayudarnos a ofrecer información clara a los asistentes.");
        message.setCreatedAt(LocalDateTime.of(2026,10,21,10,30));
        Event event = new Event();
        ReflectionTestUtils.setField(event,"id",22L);
        event.setTitle("Encuentro Musical");
        event.setCity("Lima");
        message.setRelatedEvent(event);
        when(messages.markAsRead(1L)).thenReturn(message);
        when(messages.findById(1L)).thenReturn(message);
    }

    @ParameterizedTest
    @EnumSource(value=Role.class,names={"ADMIN","ORGANIZER","DESIGNER","DEVELOPER","CLIENT"})
    void allSenderRolesUseTheSharedReaderAndExistingStatusAction(Role source) throws Exception {
        message.setSenderRole(source);
        Role recipient = source == Role.ADMIN ? Role.ORGANIZER : Role.ADMIN;
        message.setTargetRole(recipient);
        String html = mvc.perform(get("/communications/1").with(user("reader@example.test").roles(recipient.name())))
                .andExpect(status().isOk()).andExpect(view().name("communications/detail"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-source=\""+source.name()+"\"", "communications-detail.css",
                "Observaciones para la publicación", "Revisión de evento", "Normal", "Observado",
                "Encuentro Musical", "action=\"/communications/1/status\"", "name=\"_csrf\"")
                .doesNotContain(">EVENT_REVIEW<", ">OBSERVED<", "checkout-layout", "dashboard-hero");
        verify(messages).markAsRead(1L);
        saveProof(source.name()+".html", html);
    }

    @Test
    void customerSupportKeepsTheReadOnlyViewAndChecksOwnership() throws Exception {
        message.setSenderRole(Role.CLIENT);
        message.setTargetRole(Role.ADMIN);
        message.setType(MessageType.CUSTOMER_SUPPORT);
        message.setSenderEmail("customer@example.test");
        message.setRelatedEvent(null);
        String html = mvc.perform(get("/communications/1").with(user("customer@example.test").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Atención al cliente", "Tu consulta está dirigida a Administración", "Mis consultas")
                .doesNotContain("reader-status-form", "Cambiar estado", "Evento relacionado");
        verify(messages, never()).markAsRead(anyLong());
        mvc.perform(get("/communications/1").with(user("other@example.test").roles("CLIENT")))
                .andExpect(redirectedUrl("/communications"));
        saveProof("SUPPORT.html",html);
    }

    @Test
    void bodyIsEscapedAndLineBreaksArePreserved() throws Exception {
        message.setBody("Primera línea\n\n<script>alert('test')</script>");
        String html=mvc.perform(get("/communications/1").with(user("org@example.test").roles("ORGANIZER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Primera línea\n\n&lt;script&gt;").doesNotContain("<script>alert");
    }

    @Test
    void statusStillPostsWithCsrfAndClientsCannotManageIt() throws Exception {
        mvc.perform(post("/communications/1/status").with(user("admin@example.test").roles("ADMIN"))
                .with(csrf()).param("status","RESOLVED")).andExpect(redirectedUrl("/communications/1"));
        verify(messages).updateStatus(1L,MessageStatus.RESOLVED);
        mvc.perform(post("/communications/1/status").with(user("customer@example.test").roles("CLIENT"))
                .with(csrf()).param("status","ARCHIVED")).andExpect(redirectedUrl("/communications"));
        verify(messages,never()).updateStatus(1L,MessageStatus.ARCHIVED);
    }

    private static void saveProof(String name,String html) throws Exception {
        String directory=System.getProperty("connectify.visualProofDir");
        if(directory==null)return;
        Path path=Path.of(directory);
        Files.createDirectories(path);
        Files.writeString(path.resolve(name),html);
    }
}
