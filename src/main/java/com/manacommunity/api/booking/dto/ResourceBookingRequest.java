package com.manacommunity.api.booking.dto;

import com.manacommunity.common.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ResourceBookingRequest {

    @NotNull
    private Long resourceId;

    @NotNull
    private String bookingDate;

    @NotNull
    private String startTime;

    @NotNull
    private String endTime;

    private String endDate;
    private String purpose;
    private Integer numberOfGuests;
    private List<Long> equipmentIds;
    private String couponCode;

    // Recurring booking support
    private Boolean isRecurring;
    private Integer recurringOccurrences;
    private String recurringFrequency; // "DAILY" or "WEEKLY"
}
