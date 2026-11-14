package com.cocos.trading.controller.mapper;

import com.cocos.trading.api.common.InstrumentTypeApi;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.request.TradeOrderRequestApi;
import com.cocos.trading.api.request.TransferOrderRequestApi;
import com.cocos.trading.api.response.AssetPositionApi;
import com.cocos.trading.api.response.InstrumentApi;
import com.cocos.trading.api.response.InstrumentSummaryApi;
import com.cocos.trading.api.response.OrderApi;
import com.cocos.trading.api.response.PortfolioApi;
import com.cocos.trading.api.response.TradeOrderApi;
import com.cocos.trading.api.response.TransferOrderApi;
import com.cocos.trading.api.response.UserSummaryApi;
import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.service.AccountLedgerCalculator;
import com.cocos.trading.domain.model.AssetPosition;
import com.cocos.trading.domain.model.InstrumentQuote;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.domain.model.Portfolio;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Utility mapper converting between polymorphic API contract DTOs (`*Api`), application commands/models, and JPA entities.
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
     * Converts a polymorphic {@link OrderCreateRequestApi} ({@link TradeOrderRequestApi} or {@link TransferOrderRequestApi})
     * and {@code Idempotency-Key} UUID to a {@link PlaceOrderCommand} using Java 21 exhaustive pattern matching.
     *
     * @param idempotencyKey Mandatory UUID idempotency key from the request header.
     * @param request        The polymorphic API order creation request.
     * @return The application {@link PlaceOrderCommand}.
     */
    public static PlaceOrderCommand toCommand(UUID idempotencyKey, OrderCreateRequestApi request) {
        return switch (request) {
            case TradeOrderRequestApi trade -> PlaceOrderCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(trade.userId())
                    .instrumentId(trade.instrumentId())
                    .side(OrderSide.valueOf(trade.side().name()))
                    .type(OrderType.valueOf(trade.type().name()))
                    .quantity(trade.quantity())
                    .amount(toDomainMonetaryAmount(trade.amount()))
                    .price(toDomainMonetaryAmount(trade.price()))
                    .build();
            case TransferOrderRequestApi transfer -> PlaceOrderCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(transfer.userId())
                    .side(OrderSide.valueOf(transfer.side().name()))
                    .type(OrderType.MARKET)
                    .amount(toDomainMonetaryAmount(transfer.amount()))
                    .build();
        };
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
     * Converts a {@link Portfolio} to a {@link PortfolioApi}, grouping user metadata under {@code user}
     * and instrument metadata under each position's {@code instrument}.
     *
     * @param portfolio The portfolio valuation read-model.
     * @return The {@link PortfolioApi} response DTO.
     */
    public static PortfolioApi toApi(Portfolio portfolio) {
        List<AssetPositionApi> positions = portfolio.positions().stream()
                .map(ApiMapper::toApi)
                .toList();

        return PortfolioApi.builder()
                .user(toUserSummaryApi(portfolio.user()))
                .totalAccountValue(toContractMonetaryAmount(portfolio.totalAccountValue()))
                .availableCash(toContractMonetaryAmount(portfolio.availableCash()))
                .positions(positions)
                .build();
    }

    /**
     * Converts an {@link AssetPosition} to an {@link AssetPositionApi}.
     *
     * @param position The asset position read-model.
     * @return The {@link AssetPositionApi} response DTO.
     */
    public static AssetPositionApi toApi(AssetPosition position) {
        return AssetPositionApi.builder()
                .instrument(toInstrumentSummaryApi(position.instrument()))
                .quantity(position.quantity())
                .lastPrice(toContractMonetaryAmount(position.lastPrice()))
                .marketValue(toContractMonetaryAmount(position.marketValue()))
                .totalReturnPercentage(position.totalReturnPercentage())
                .dailyReturnPercentage(position.dailyReturnPercentage())
                .build();
    }

    /**
     * Converts an {@link InstrumentQuote} to an {@link InstrumentApi}.
     *
     * @param quote The instrument quote projection.
     * @return The {@link InstrumentApi} response DTO.
     */
    public static InstrumentApi toApi(InstrumentQuote quote) {
        return InstrumentApi.builder()
                .id(quote.instrument().getId())
                .ticker(quote.instrument().getTicker())
                .name(quote.instrument().getName())
                .type(InstrumentTypeApi.valueOf(quote.instrument().getType().name()))
                .lastPrice(toContractMonetaryAmount(quote.lastPrice()))
                .previousClose(toContractMonetaryAmount(quote.previousClose()))
                .dailyReturnPercentage(quote.dailyReturnPercentage())
                .build();
    }

    /**
     * Converts a polymorphic {@link OrderJpaEntity} directly to its matching {@link OrderApi} subtype using Java 21 pattern matching:
     * <ul>
     *   <li>{@link TradeOrderJpaEntity} ({@code BUY}/{@code SELL}) &rarr; {@link TradeOrderApi} (populates {@code quantity} and {@code price}).</li>
     *   <li>{@link TransferOrderJpaEntity} ({@code CASH_IN}/{@code CASH_OUT}) &rarr; {@link TransferOrderApi} (populates {@code amount}).</li>
     * </ul>
     *
     * @param order The persisted {@link OrderJpaEntity}.
     * @return The polymorphic {@link OrderApi} response DTO ({@link TradeOrderApi} or {@link TransferOrderApi}).
     */
    public static OrderApi toApi(OrderJpaEntity order) {
        String formattedDatetime = order.getDatetime() != null
                ? order.getDatetime().format(ISO_DATETIME_FORMATTER)
                : null;

        return switch (order) {
            case TransferOrderJpaEntity transferOrder -> TransferOrderApi.builder()
                    .id(transferOrder.getId())
                    .user(toUserSummaryApi(transferOrder.getUser()))
                    .amount(toContractMonetaryAmount(AccountLedgerCalculator.calculateTotalAmount(transferOrder)))
                    .side(OrderSideApi.valueOf(transferOrder.getSide().name()))
                    .status(OrderStatusApi.valueOf(transferOrder.getStatus().name()))
                    .datetime(formattedDatetime)
                    .build();
            case TradeOrderJpaEntity tradeOrder -> TradeOrderApi.builder()
                    .id(tradeOrder.getId())
                    .user(toUserSummaryApi(tradeOrder.getUser()))
                    .instrument(toInstrumentSummaryApi(tradeOrder.getInstrument()))
                    .quantity(tradeOrder.getQuantity())
                    .price(tradeOrder.getPrice() != null
                            ? com.cocos.trading.api.common.MonetaryAmount.ars(tradeOrder.getPrice())
                            : null)
                    .type(OrderTypeApi.valueOf(tradeOrder.getType().name()))
                    .side(OrderSideApi.valueOf(tradeOrder.getSide().name()))
                    .status(OrderStatusApi.valueOf(tradeOrder.getStatus().name()))
                    .datetime(formattedDatetime)
                    .build();
            default -> throw new IllegalStateException("Unsupported OrderJpaEntity subtype: " + order.getClass().getName());
        };
    }

    private static UserSummaryApi toUserSummaryApi(UserJpaEntity user) {
        if (user == null) {
            return null;
        }
        return UserSummaryApi.builder()
                .id(user.getId())
                .accountNumber(user.getAccountNumber())
                .build();
    }

    private static InstrumentSummaryApi toInstrumentSummaryApi(InstrumentJpaEntity instrument) {
        if (instrument == null) {
            return null;
        }
        return InstrumentSummaryApi.builder()
                .id(instrument.getId())
                .ticker(instrument.getTicker())
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
