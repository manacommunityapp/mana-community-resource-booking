package com.manacommunity.api.booking.dto;

import com.manacommunity.common.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingRuleRequest {

    @NotNull
    private String pricingType;

    private Long resourceId;
    private Long categoryId;
    private BigDecimal basePrice;
    private BigDecimal peakPrice;
    private BigDecimal offPeakPrice;
    private BigDecimal depositAmount;
    private BigDecimal taxRate;
    private String currency;
    private String description;
    private Boolean isActive;
    private String dayOfWeek;
    private String validFrom;
    private String validTo;
    private String startTime;
    private String endTime;
    private BigDecimal amount;
    private BigDecimal percentage;
}

