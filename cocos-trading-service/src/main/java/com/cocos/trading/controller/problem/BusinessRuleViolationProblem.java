package com.cocos.trading.controller.problem;

import java.net.URI;
import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * RFC 7807 Problem representation for 409 Conflict / Business Rule / Order
 * State violations.
 */
public class BusinessRuleViolationProblem extends AbstractThrowableProblem {

    public static final URI TYPE_URI = URI
            .create("https://api.cocos.com/problems/trading-api/business-rule-violation/409");

    public BusinessRuleViolationProblem(String title, Status status, String detail, Map<String, Object> parameters) {
        super(
                TYPE_URI,
                title,
                status,
                detail,
                null,
                null,
                parameters != null ? Map.of("parameters", parameters) : null);
    }
}
