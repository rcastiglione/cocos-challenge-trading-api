package com.cocos.trading.controller.mapper;

import com.cocos.trading.api.common.InstrumentTypeApi;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.response.AssetPositionApi;
import com.cocos.trading.api.response.InstrumentApi;
import com.cocos.trading.api.response.OrderApi;
import com.cocos.trading.api.response.PortfolioApi;
import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.AssetPosition;
import com.cocos.trading.domain.model.InstrumentQuote;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.domain.model.Portfolio;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility mapper converting between API contract DTOs (`*Api`) and application/domain models.
 */
public final class ApiMapper {

    private static final DateTimeFormatter ISO_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private ApiMapper() {
    }

    /**
     * Converts an {@link InstrumentSearchFilterRequestApi} to an {@link InstrumentSearchQuery}.
     *
     * @param filter The API search filter.
     * @return The application {@link InstrumentSearchQuery}.
     */
    public static InstrumentSearchQuery toQuery(InstrumentSearchFilterRequestApi filter) {
        if (filter == null) {
            return new InstrumentSearchQuery(null, null, null);
        }
        return new InstrumentSearchQuery(filter.query(), filter.ticker(), filter.name());
    }

    /**
     * Converts an {@link OrderCreateRequestApi} to a {@link PlaceOrderCommand}.
     *
     * @param request The API order creation request.
     * @return The application {@link PlaceOrderCommand}.
     */
    public static PlaceOrderCommand toCommand(OrderCreateRequestApi request) {
        return new PlaceOrderCommand(
                request.userId(),
                request.instrumentId(),
                OrderSide.valueOf(request.side().name()),
                OrderType.valueOf(request.type().name()),
                request.size(),
                request.amount(),
                request.price()
        );
    }

    /**
     * Converts a domain {@link Portfolio} to a {@link PortfolioApi}.
     *
     * @param portfolio The domain portfolio.
     * @return The {@link PortfolioApi} response DTO.
     */
    public static PortfolioApi toApi(Portfolio portfolio) {
        List<AssetPositionApi> positions = portfolio.positions().stream()
                .map(ApiMapper::toApi)
                .toList();

        return new PortfolioApi(
                portfolio.userId(),
                portfolio.accountNumber(),
                portfolio.totalAccountValue(),
                portfolio.availableCash(),
                positions
        );
    }

    /**
     * Converts a domain {@link AssetPosition} to an {@link AssetPositionApi}.
     *
     * @param position The domain asset position.
     * @return The {@link AssetPositionApi} response DTO.
     */
    public static AssetPositionApi toApi(AssetPosition position) {
        return new AssetPositionApi(
                position.instrumentId(),
                position.ticker(),
                position.name(),
                position.quantity(),
                position.lastPrice(),
                position.marketValue(),
                position.totalReturnPercentage(),
                position.dailyReturnPercentage()
        );
    }

    /**
     * Converts a domain {@link InstrumentQuote} to an {@link InstrumentApi}.
     *
     * @param quote The domain instrument quote.
     * @return The {@link InstrumentApi} response DTO.
     */
    public static InstrumentApi toApi(InstrumentQuote quote) {
        return new InstrumentApi(
                quote.instrument().id(),
                quote.instrument().ticker(),
                quote.instrument().name(),
                InstrumentTypeApi.valueOf(quote.instrument().type().name()),
                quote.lastPrice(),
                quote.previousClose(),
                quote.dailyReturnPercentage()
        );
    }

    /**
     * Converts a domain {@link Order} to an {@link OrderApi}.
     *
     * @param order The domain order.
     * @return The {@link OrderApi} response DTO.
     */
    public static OrderApi toApi(Order order) {
        return new OrderApi(
                order.id(),
                order.instrumentId(),
                order.userId(),
                order.size(),
                order.price(),
                OrderTypeApi.valueOf(order.type().name()),
                OrderSideApi.valueOf(order.side().name()),
                OrderStatusApi.valueOf(order.status().name()),
                order.datetime() != null ? order.datetime().format(ISO_DATETIME_FORMATTER) : null
        );
    }
}

