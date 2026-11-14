package com.cocos.trading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Semantic summary of the user identity and trading account within portfolio responses.
 *
 * @param id            Unique identifier of the user.
 * @param accountNumber User's trading account number.
 */
@Builder
@Schema(description = "User identity and trading account summary")
public record UserSummaryApi(
        @Schema(description = "User ID", example = "1")
        Long id,

        @Schema(description = "User trading account number", example = "10001")
        String accountNumber
) {
}

