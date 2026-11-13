package com.cocos.trading.controller.problem;

import java.net.URI;
import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * RFC 7807 Problem representation for 400 Bad Request errors.
 */
public class BadRequestProblem extends AbstractThrowableProblem {

    public static final URI TYPE_URI = URI.create("https://api.cocos.com/problems/trading-api/bad-request/400");

    public BadRequestProblem(String message) {
        this(message, null);
    }

    public BadRequestProblem(String message, Map<String, String> parameters) {
        super(
                TYPE_URI,
                "Bad Request",
                Status.BAD_REQUEST,
                message,
                null,
                null,
                parameters != null ? Map.of("parameters", parameters) : null
        );
    }
}
