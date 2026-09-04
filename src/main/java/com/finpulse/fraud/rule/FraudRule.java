package com.finpulse.fraud.rule;

import com.finpulse.fraud.context.AnalysisContext;
import com.finpulse.fraud.model.RuleResult;

public interface FraudRule {
    String ruleCode();

    RuleResult evaluate(String senderAccount, AnalysisContext context);
}
