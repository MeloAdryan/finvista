package com.finvista.dto;
import com.finvista.model.CostBehavior;
import com.finvista.model.CostNature;

public record CostClassificationDto(String categoria, CostBehavior comportamento, CostNature natureza) {}