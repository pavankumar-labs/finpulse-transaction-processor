package com.finpulse.fraud.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class RuleResult {

    private final String ruleCode;
    private final boolean triggered;
    private final int score;
    private final String reason;
    private final Map<String, Object> evidence;
}
