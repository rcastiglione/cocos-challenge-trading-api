package com.cocos.trading.controller.problem;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * RFC 7807 Problem representation for 400 Bad Request errors.
 */
public class BadRequestProblem extends ProblemDetail {

    public static final URI TYPE_URI = URI.create("https://api.cocos.com/problems/trading-api/bad-request/400");

    public BadRequestProblem(String message) {
        this(message, null);
    }

    public BadRequestProblem(String message, Map<String, String> parameters) {
        super(HttpStatus.BAD_REQUEST.value());
        setType(TYPE_URI);
        setTitle("Bad Request");
        setDetail(message);
        if (parameters != null) {
            setProperty("parameters", parameters);
        }
    }
}
