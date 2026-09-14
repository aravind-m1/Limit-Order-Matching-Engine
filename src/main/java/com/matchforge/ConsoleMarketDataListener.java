package com.matchforge;

import com.matchforge.marketdata.MarketDataListener;
import com.matchforge.model.Trade;

public final class ConsoleMarketDataListener implements MarketDataListener {
    @Override
    public void onTrade(Trade trade) {
        System.out.println("TRADE  " + trade);
    }

    @Override
    public void onBookUpdate(String symbol, Long bestBid, Long bestAsk) {
        System.out.println("BOOK   " + symbol + " bestBid=" + bestBid + " bestAsk=" + bestAsk);
    }
}
