package io.github.yesh.paycore.settlement.dto;

import java.util.Map;

public record SimulationRequest(
    String batchId, Long seed, Map<String, Double> faultRates, String filename) {}
