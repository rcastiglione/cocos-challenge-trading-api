package com.cocos.trading.controller.problem;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * RFC 7807 Problem representation for 409 Conflict / Business Rule / Order
 * State violations.
 */
public class BusinessRuleViolationProblem extends ProblemDetail {

    public static final URI TYPE_URI = URI
            .create("https://api.cocos.com/problems/trading-api/business-rule-violation/409");

    public BusinessRuleViolationProblem(String title, HttpStatus status, String detail, Map<String, Object> parameters) {
        super(status.value());
        setType(TYPE_URI);
        setTitle(title);
        setDetail(detail);
        if (parameters != null) {
            setProperty("parameters", parameters);
        }
    }
}
