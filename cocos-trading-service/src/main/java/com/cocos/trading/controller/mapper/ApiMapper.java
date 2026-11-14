package com.cocos.trading.controller.mapper;

import com.cocos.trading.api.common.InstrumentTypeApi;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.response.AssetPositionApi;
import com.cocos.trading.api.response.InstrumentApi;
import com.cocos.trading.api.response.InstrumentSummaryApi;
import com.cocos.trading.api.response.OrderApi;
import com.cocos.trading.api.response.PortfolioApi;
import com.cocos.trading.api.response.UserSummaryApi;
import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.AssetPosition;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.InstrumentQuote;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.domain.model.Portfolio;
import com.cocos.trading.domain.model.User;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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
            return InstrumentSearchQuery.builder().build();
        }
        return InstrumentSearchQuery.builder()
                .query(filter.query())
                .ticker(filter.ticker())
                .name(filter.name())
                .build();
    }

    /**
     * Converts an {@link OrderCreateRequestApi} and {@code Idempotency-Key} UUID to a {@link PlaceOrderCommand}.
     *
     * @param idempotencyKey Mandatory UUID idempotency key from the request header.
     * @param request        The API order creation request.
     * @return The application {@link PlaceOrderCommand}.
     */
    public static PlaceOrderCommand toCommand(UUID idempotencyKey, OrderCreateRequestApi request) {
        return PlaceOrderCommand.builder()
                .idempotencyKey(idempotencyKey)
                .userId(request.userId())
                .instrumentId(request.instrumentId())
                .side(OrderSide.valueOf(request.side().name()))
                .type(OrderType.valueOf(request.type().name()))
                .size(request.size())
                .amount(toDomainMonetaryAmount(request.amount()))
                .price(toDomainMonetaryAmount(request.price()))
                .build();
    }

    /**
     * Converts an {@link OrderCreateRequestApi} to a {@link PlaceOrderCommand}.
     *
     * @param request The API order creation request.
     * @return The application {@link PlaceOrderCommand}.
     */
    public static PlaceOrderCommand toCommand(OrderCreateRequestApi request) {
        return toCommand(null, request);
    }

    /**
     * Converts a domain {@link Portfolio} to a {@link PortfolioApi}, grouping user metadata under {@code user}
     * and instrument metadata under each position's {@code instrument}.
     *
     * @param portfolio The domain portfolio.
     * @return The {@link PortfolioApi} response DTO.
     */
    public static PortfolioApi toApi(Portfolio portfolio) {
        List<AssetPositionApi> positions = portfolio.positions().stream()
                .map(ApiMapper::toApi)
                .toList();

        User user = portfolio.user();
        UserSummaryApi userSummary = user != null
                ? UserSummaryApi.builder()
                        .id(user.id())
                        .accountNumber(user.accountNumber())
                        .build()
                : null;

        return PortfolioApi.builder()
                .user(userSummary)
                .totalAccountValue(toContractMonetaryAmount(portfolio.totalAccountValue()))
                .availableCash(toContractMonetaryAmount(portfolio.availableCash()))
                .positions(positions)
                .build();
    }

    /**
     * Converts a domain {@link AssetPosition} to an {@link AssetPositionApi}.
     *
     * @param position The domain asset position.
     * @return The {@link AssetPositionApi} response DTO.
     */
    public static AssetPositionApi toApi(AssetPosition position) {
        Instrument instrument = position.instrument();
        InstrumentSummaryApi instrumentSummary = instrument != null
                ? InstrumentSummaryApi.builder()
                        .id(instrument.id())
                        .ticker(instrument.ticker())
                        .build()
                : null;

        return AssetPositionApi.builder()
                .instrument(instrumentSummary)
                .quantity(position.quantity())
                .lastPrice(toContractMonetaryAmount(position.lastPrice()))
                .marketValue(toContractMonetaryAmount(position.marketValue()))
                .totalReturnPercentage(position.totalReturnPercentage())
                .dailyReturnPercentage(position.dailyReturnPercentage())
                .build();
    }

    /**
     * Converts a domain {@link InstrumentQuote} to an {@link InstrumentApi}.
     *
     * @param quote The domain instrument quote.
     * @return The {@link InstrumentApi} response DTO.
     */
    public static InstrumentApi toApi(InstrumentQuote quote) {
        return InstrumentApi.builder()
                .id(quote.instrument().id())
                .ticker(quote.instrument().ticker())
                .name(quote.instrument().name())
                .type(InstrumentTypeApi.valueOf(quote.instrument().type().name()))
                .lastPrice(toContractMonetaryAmount(quote.lastPrice()))
                .previousClose(toContractMonetaryAmount(quote.previousClose()))
                .dailyReturnPercentage(quote.dailyReturnPercentage())
                .build();
    }

    /**
     * Converts a domain {@link Order} to an {@link OrderApi}.
     *
     * @param order The domain order.
     * @return The {@link OrderApi} response DTO.
     */
    public static OrderApi toApi(Order order) {
        return OrderApi.builder()
                .id(order.getId())
                .instrumentId(order.getInstrumentId())
                .userId(order.getUserId())
                .size(order.getSize())
                .price(toContractMonetaryAmount(order.getPrice()))
                .type(OrderTypeApi.valueOf(order.getType().name()))
                .side(OrderSideApi.valueOf(order.getSide().name()))
                .status(OrderStatusApi.valueOf(order.getStatus().name()))
                .datetime(order.getDatetime() != null ? order.getDatetime().format(ISO_DATETIME_FORMATTER) : null)
                .build();
    }

    private static MonetaryAmount toDomainMonetaryAmount(com.cocos.trading.api.common.MonetaryAmount contractAmount) {
        if (contractAmount == null || contractAmount.amount() == null) {
            return null;
        }
        String currency = (contractAmount.currency() != null && !contractAmount.currency().isBlank())
                ? contractAmount.currency()
                : MonetaryAmount.ARS_CURRENCY;
        return MonetaryAmount.of(contractAmount.amount(), currency);
    }

    private static com.cocos.trading.api.common.MonetaryAmount toContractMonetaryAmount(MonetaryAmount domainAmount) {
        if (domainAmount == null) {
            return null;
        }
        return com.cocos.trading.api.common.MonetaryAmount.builder()
                .amount(domainAmount.amount())
                .currency(domainAmount.currency())
                .build();
    }
}
