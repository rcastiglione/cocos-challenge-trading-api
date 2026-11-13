package com.cocos.trading.api;

import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.response.InstrumentApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contract interface for searching market instruments.
 */
@Tag(name = "Instruments", description = "Operations related to market instruments search")
@RequestMapping("/instruments")
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
    @GetMapping
    List<InstrumentApi> search(
            @ModelAttribute InstrumentSearchFilterRequestApi filter
    );
}

