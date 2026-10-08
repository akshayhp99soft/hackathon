package com.lyxor.billing.model;

import java.math.BigDecimal;
import java.util.Objects;

public class TaxJurisdiction {
    private final String countryCode;
    private final String region;
    private final BigDecimal standardTaxRate;

    public TaxJurisdiction(String countryCode, String region, BigDecimal standardTaxRate) {
        this.countryCode = Objects.requireNonNull(countryCode);
        this.region = region != null ? region : "ALL";
        this.standardTaxRate = standardTaxRate != null ? standardTaxRate : BigDecimal.ZERO;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegion() {
        return region;
    }

    public BigDecimal getStandardTaxRate() {
        return standardTaxRate;
    }
}
