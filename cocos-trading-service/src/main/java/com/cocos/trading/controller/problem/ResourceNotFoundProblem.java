package com.cocos.trading.controller.problem;

import java.net.URI;
import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * RFC 7807 Problem representation for 404 Not Found errors.
 */
public class ResourceNotFoundProblem extends AbstractThrowableProblem {

    public static final URI TYPE_URI = URI.create("https://api.cocos.com/problems/trading-api/not-found/404");

    public ResourceNotFoundProblem(String resourceName, Object identifier, String detail) {
        super(
                TYPE_URI,
                "Resource Not Found",
                Status.NOT_FOUND,
                detail,
                null,
                null,
                Map.of(
                        "parameters",
                        Map.of(
                                "resource", resourceName,
                                "identifier", String.valueOf(identifier)
                        )
                )
        );
    }
}
