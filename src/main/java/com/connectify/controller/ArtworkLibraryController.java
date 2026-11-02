package com.connectify.controller;

import com.connectify.service.ArtworkLibraryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class ArtworkLibraryController {
    private final ArtworkLibraryService library;

    public ArtworkLibraryController(ArtworkLibraryService library) { this.library = library; }

    @GetMapping("/dashboard/artwork")
    public String library(Model model) throws IOException {
        model.addAttribute("artworks", library.list());
        return "dashboard/artwork-library";
    }
}
