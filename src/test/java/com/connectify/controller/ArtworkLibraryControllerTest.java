package com.connectify.controller;

import com.connectify.security.DatabaseUserDetailsService;
import com.connectify.security.SecurityConfig;
import com.connectify.service.ArtworkLibraryService;
import com.connectify.service.ImageStorageService;
import com.connectify.config.UploadResourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.nio.file.Files;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(ArtworkLibraryControllerTest.Config.class)
@ActiveProfiles("prod")
class ArtworkLibraryControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired ImageStorageService storage;
    MockMvc mvc;

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DEVELOPER"})
    void authorizedRolesCanSeeImagesAndSpiderButNoVideos(String role) throws Exception {
        mvc.perform(get("/dashboard/artwork").with(user("art@example.test").roles(role)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Imágenes &amp; arte")))
                .andExpect(content().string(containsString("marketplace-hero.png")))
                .andExpect(content().string(containsString("Arañita de Halloween")))
                .andExpect(content().string(not(containsString(".mp4"))));
    }

    @ParameterizedTest @ValueSource(strings = {"CLIENT", "ORGANIZER", "DESIGNER", "GATE_AGENT"})
    void otherRolesCannotBrowseTheSharedCatalogue(String role) throws Exception {
        mvc.perform(get("/dashboard/artwork").with(user("other@example.test").roles(role))).andExpect(status().isForbidden());
    }

    @Test void anonymousVisitorsMustLogin() throws Exception {
        mvc.perform(get("/dashboard/artwork")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test void formerOrganizerLibraryIsBlocked() throws Exception {
        mvc.perform(get("/dashboard/organizer/artwork").with(user("owner@example.test").roles("ORGANIZER")))
                .andExpect(status().isForbidden());
    }

    @Test void uploadedImagesAreServedFromTheConfiguredPersistentDirectory() throws Exception {
        byte[] image = new byte[]{1, 2, 3};
        String url = storage.store(new MockMultipartFile("imageFile", "cover.png", "image/png", image), "presentations");
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(image));
    }

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ArtworkLibraryController.class, ArtworkLibraryService.class, UploadResourceConfig.class})
    static class Config {
        @Bean ImageStorageService imageStorageService() throws Exception {
            return new ImageStorageService(Files.createTempDirectory("artwork-controller-test-").toString());
        }
        @Bean DatabaseUserDetailsService databaseUserDetailsService() {
            // These requests use Spring Security test principals, never credential authentication.
            return new DatabaseUserDetailsService(null);
        }
        @Bean ThymeleafViewResolver thymeleafViewResolver() {
            var resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
            var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
            var view = new ThymeleafViewResolver(); view.setTemplateEngine(engine); view.setCharacterEncoding("UTF-8");
            return view;
        }
    }
}
