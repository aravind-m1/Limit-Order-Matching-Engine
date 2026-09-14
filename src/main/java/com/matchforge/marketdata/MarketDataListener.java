package com.matchforge.marketdata;

import com.matchforge.model.Trade;

public interface MarketDataListener {
    void onTrade(Trade trade);
    void onBookUpdate(String symbol, Long bestBid, Long bestAsk);
}
