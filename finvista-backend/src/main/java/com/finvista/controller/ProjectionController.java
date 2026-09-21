package com.finvista.controller;

import com.finvista.dto.ProjectionResponse;
import com.finvista.service.ProjectionService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projecao")
public class ProjectionController {

    private final ProjectionService projectionService;

    public ProjectionController(
            ProjectionService projectionService
    ) {
        this.projectionService =
                projectionService;
    }

    @GetMapping
    public List<ProjectionResponse> getProjecao() {

        return projectionService
                .obterProjecao();
    }
}