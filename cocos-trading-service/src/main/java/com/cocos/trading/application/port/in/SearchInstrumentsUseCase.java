package com.cocos.trading.application.port.in;

import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.domain.model.InstrumentQuote;
import java.util.List;

/**
 * Inbound port (use case) for searching market instruments.
 */
public interface SearchInstrumentsUseCase {

    /**
     * Searches instruments matching the provided query criteria and enriches them with latest market quotes.
     *
     * @param query The search filter criteria.
     * @return List of matching {@link InstrumentQuote} records.
     */
    List<InstrumentQuote> search(InstrumentSearchQuery query);
}

