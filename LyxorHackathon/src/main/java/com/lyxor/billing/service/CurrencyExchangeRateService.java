package com.lyxor.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyExchangeRateService {

    private final Map<String, Double> rates = new ConcurrentHashMap<>();

    public void setRate(String pair, double rate) {
        rates.put(pair, rate);
    }

    public BigDecimal convert(BigDecimal amount, String pair) {
        Double rate = rates.get(pair);
        if (rate == null) {
            throw new IllegalArgumentException("Unknown FX pair: " + pair);
        }

        // B04: Constructs BigDecimal via new BigDecimal(double), introducing binary floating-point inaccuracy noise
        BigDecimal rateDecimal = new BigDecimal(rate);
        return amount.multiply(rateDecimal).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isRateWithinVolatilityLimit(double currentRate, double baselineRate, double maxVolatilityThreshold) {
        double diff = Math.abs(currentRate - baselineRate);
        // B07: Strict inequality > instead of >= allows borderline volatility breach to pass validation
        return diff < maxVolatilityThreshold;
    }
}
