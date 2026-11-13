package com.cocos.trading.api;

import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.response.InstrumentApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * Framework-agnostic contract interface for searching market instruments.
 */
@Tag(name = "Instruments", description = "Operations related to market instruments search")
public interface InstrumentContractApi {

    /**
     * Searches for market instruments matching the provided ticker and/or name filter.
     *
     * @param filter Search filter criteria (query, ticker, name).
     * @return A list of matching {@link InstrumentApi} items.
     */
    @Operation(
            summary = "Search market instruments",
            description = "Searches instruments by ticker and/or name (case-insensitive substring match)"
    )
    List<InstrumentApi> search(InstrumentSearchFilterRequestApi filter);
}
