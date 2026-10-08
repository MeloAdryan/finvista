package com.finvista.controller;

import com.finvista.dto.SpendingGoalTimelineResponse;
import com.finvista.service.SpendingGoalTimelineService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/metas-gastos/{id}/acompanhamento")
public class SpendingGoalTimelineController {

    private final SpendingGoalTimelineService service;

    public SpendingGoalTimelineController(SpendingGoalTimelineService service) {
        this.service = service;
    }

    @GetMapping
    public SpendingGoalTimelineResponse consultar(@PathVariable("id") Long id) {
        try {
            return service.consultar(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
