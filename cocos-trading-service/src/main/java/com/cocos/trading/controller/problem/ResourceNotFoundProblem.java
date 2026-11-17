package com.cocos.trading.controller.problem;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * RFC 7807 Problem representation for 404 Not Found errors.
 */
public class ResourceNotFoundProblem extends ProblemDetail {

    public static final URI TYPE_URI = URI.create("https://api.cocos.com/problems/trading-api/not-found/404");

    public ResourceNotFoundProblem(String resourceName, Object identifier, String detail) {
        super(HttpStatus.NOT_FOUND.value());
        setType(TYPE_URI);
        setTitle("Resource Not Found");
        setDetail(detail);
        setProperty("parameters", Map.of(
                "resource", resourceName,
                "identifier", String.valueOf(identifier)
        ));
    }
}
