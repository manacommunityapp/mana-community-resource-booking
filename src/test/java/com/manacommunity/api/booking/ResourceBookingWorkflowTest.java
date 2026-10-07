package com.manacommunity.api.booking;

import com.manacommunity.api.booking.dto.*;
import com.manacommunity.api.booking.entity.*;
import com.manacommunity.api.booking.entity.enums.*;
import com.manacommunity.api.booking.repository.*;
import com.manacommunity.api.booking.service.*;
import com.manacommunity.common.model.Community;
import com.manacommunity.common.user.model.AppUser;
import com.manacommunity.common.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ResourceBookingWorkflowTest {

    @Autowired
    private ResourceBookingService bookingService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceCategoryService categoryService;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private BookingRuleService ruleService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ResourceCategoryRepository categoryRepository;

    @Autowired
    private ResourceBookingRepository bookingRepository;

    @Autowired
    private ResourceScheduleRepository scheduleRepository;

    @Autowired
    private BookingWaitlistRepository waitlistRepository;

    @Autowired
    private PricingRuleRepository pricingRepository;

    @Autowired
    private BusinessRuleRepository businessRuleRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Community testCommunity;
    private AppUser residentUser;
    private AppUser secondUser;
    private AppUser adminUser;
    private ResourceCategory clubhouseCategory;

    @BeforeEach
    void setUp() {
        testCommunity = Community.builder()
                .name("Palm Meadows")
                .type("APARTMENT")
                .city("Bangalore")
                .state("Karnataka")
                .active(true)
                .build();
        entityManager.persist(testCommunity);
        entityManager.flush();

        residentUser = AppUser.builder()
                .fullName("Rahul Sharma")
                .email("rahul.sharma@example.com")
                .phone("9876543210")
                .passwordHash("secret-hash")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("MALE")
                .role("RESIDENT")
                .community(testCommunity)
                .build();
        residentUser = userRepository.save(residentUser);

        secondUser = AppUser.builder()
                .fullName("Pooja Nair")
                .email("pooja.nair@example.com")
                .phone("9876543211")
                .passwordHash("secret-hash")
                .dateOfBirth(LocalDate.of(1992, 5, 15))
                .gender("FEMALE")
                .role("RESIDENT")
                .community(testCommunity)
                .build();
        secondUser = userRepository.save(secondUser);

        adminUser = AppUser.builder()
                .fullName("Community Manager")
                .email("manager@palmmeadows.com")
                .phone("9876543212")
                .passwordHash("secret-hash")
                .dateOfBirth(LocalDate.of(1985, 3, 20))
                .gender("MALE")
                .role("ADMIN")
                .community(testCommunity)
                .build();
        adminUser = userRepository.save(adminUser);

        clubhouseCategory = ResourceCategory.builder()
                .name("Sports & Club")
                .community(testCommunity)
                .status(ResourceCategory.CategoryStatus.ACTIVE)
                .displayOrder(1)
                .build();
        clubhouseCategory = categoryRepository.save(clubhouseCategory);
    }

    @Test
    @DisplayName("1. Resource creation with schedule and slot generation")
    void testResourceCreationAndSlots() {
        Resource tennisCourt = Resource.builder()
                .name("Tennis Court A")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(4)
                .openTime(LocalTime.of(6, 0))
                .closeTime(LocalTime.of(22, 0))
                .bookingDurationMinutes(60)
                .bufferTimeMinutes(0)
                .status(ResourceStatus.ACTIVE)
                .advanceBookingDays(7)
                .maxBookingsPerUser(3)
                .paymentRequired(true)
                .build();
        tennisCourt = resourceRepository.save(tennisCourt);

        // Add daily schedule
        for (DayOfWeek dow : DayOfWeek.values()) {
            ResourceSchedule sched = ResourceSchedule.builder()
                    .resource(tennisCourt)
                    .dayOfWeek(dow)
                    .openTime(LocalTime.of(6, 0))
                    .closeTime(LocalTime.of(22, 0))
                    .isAvailable(true)
                    .build();
            scheduleRepository.save(sched);
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<SlotResponse> slots = bookingService.generateSlots(tennisCourt.getId(), tomorrow);
        assertNotNull(slots);
        assertFalse(slots.isEmpty());
        assertEquals("06:00:00", slots.get(0).getStartTime());
        assertTrue(slots.get(0).isAvailable());
    }

    @Test
    @DisplayName("2. Pricing calculation: Base hourly, peak-hour and weekend adjustments")
    void testDynamicPricingCalculation() {
        Resource badmintonCourt = Resource.builder()
                .name("Badminton Court 1")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(4)
                .openTime(LocalTime.of(6, 0))
                .closeTime(LocalTime.of(22, 0))
                .bookingDurationMinutes(60)
                .status(ResourceStatus.ACTIVE)
                .paymentRequired(true)
                .build();
        badmintonCourt = resourceRepository.save(badmintonCourt);

        // Base rate: 200/hr
        PricingRule baseRule = PricingRule.builder()
                .resource(badmintonCourt)
                .community(testCommunity)
                .pricingType(PricingType.BASE_HOURLY)
                .amount(new BigDecimal("200.00"))
                .isActive(true)
                .build();
        pricingRepository.save(baseRule);

        // Peak hour: +25% between 18:00 and 21:00
        PricingRule peakRule = PricingRule.builder()
                .resource(badmintonCourt)
                .community(testCommunity)
                .pricingType(PricingType.PEAK_HOUR)
                .percentage(new BigDecimal("25.00"))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(21, 0))
                .isActive(true)
                .build();
        pricingRepository.save(peakRule);

        // 18:00 to 19:00 should have base 200 + 25% peak = 250
        LocalDate regularWeekday = LocalDate.of(2026, 10, 14); // Wednesday
        ResourceBookingRequest req = new ResourceBookingRequest();
        req.setResourceId(badmintonCourt.getId());
        req.setBookingDate(regularWeekday.toString());
        req.setStartTime("18:00:00");
        req.setEndTime("19:00:00");
        req.setPurpose("Evening match");

        ResourceBookingResponse response = bookingService.createBooking(req, residentUser, testCommunity);
        assertNotNull(response);
        assertEquals(0, new BigDecimal("250.00").compareTo(response.getTotalAmount()));
        assertEquals(BookingStatus.CONFIRMED.name(), response.getStatus());
    }

    @Test
    @DisplayName("3. Conflict detection prevents overlapping slot bookings")
    void testConflictDetection() {
        Resource partyHall = Resource.builder()
                .name("Grand Party Hall")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(100)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(23, 0))
                .status(ResourceStatus.ACTIVE)
                .build();
        partyHall = resourceRepository.save(partyHall);

        LocalDate date = LocalDate.now().plusDays(2);

        // Booking 1: 10:00 - 12:00
        ResourceBookingRequest req1 = new ResourceBookingRequest();
        req1.setResourceId(partyHall.getId());
        req1.setBookingDate(date.toString());
        req1.setStartTime("10:00:00");
        req1.setEndTime("12:00:00");
        req1.setPurpose("Birthday Party");
        bookingService.createBooking(req1, residentUser, testCommunity);

        // Booking 2: 11:00 - 13:00 (overlaps by 1 hour)
        ResourceBookingRequest req2 = new ResourceBookingRequest();
        req2.setResourceId(partyHall.getId());
        req2.setBookingDate(date.toString());
        req2.setStartTime("11:00:00");
        req2.setEndTime("13:00:00");
        req2.setPurpose("Community meetup");

        assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(req2, secondUser, testCommunity)
        );
    }

    @Test
    @DisplayName("4. Approval workflow: Resources requiring approval start in PENDING and need manager approval")
    void testApprovalWorkflow() {
        Resource clubhouseHall = Resource.builder()
                .name("Clubhouse Banquet")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(80)
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .status(ResourceStatus.ACTIVE)
                .approvalRequired(true)
                .build();
        clubhouseHall = resourceRepository.save(clubhouseHall);

        LocalDate date = LocalDate.now().plusDays(3);
        ResourceBookingRequest req = new ResourceBookingRequest();
        req.setResourceId(clubhouseHall.getId());
        req.setBookingDate(date.toString());
        req.setStartTime("10:00:00");
        req.setEndTime("12:00:00");
        req.setPurpose("Anniversary Reception");

        ResourceBookingResponse created = bookingService.createBooking(req, residentUser, testCommunity);
        assertEquals(BookingStatus.PENDING.name(), created.getStatus());
        assertEquals(ApprovalStatus.PENDING.name(), created.getApprovalStatus());

        // Approve booking
        ResourceBookingResponse approved = bookingService.approveBooking(created.getId(), adminUser, "Approved by manager");
        assertEquals(BookingStatus.CONFIRMED.name(), approved.getStatus());
        assertEquals(ApprovalStatus.APPROVED.name(), approved.getApprovalStatus());
    }

    @Test
    @DisplayName("5. Cancellation & Tiered Refund: >24h grants 100% refund, waitlist candidate is notified")
    void testCancellationTieredRefundAndWaitlistNotification() {
        Resource gymStudio = Resource.builder()
                .name("Aerobics Studio")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(20)
                .openTime(LocalTime.of(6, 0))
                .closeTime(LocalTime.of(21, 0))
                .status(ResourceStatus.ACTIVE)
                .allowWaitlist(true)
                .paymentRequired(true)
                .build();
        gymStudio = resourceRepository.save(gymStudio);

        PricingRule baseRule = PricingRule.builder()
                .resource(gymStudio)
                .community(testCommunity)
                .pricingType(PricingType.BASE_HOURLY)
                .amount(new BigDecimal("100.00"))
                .isActive(true)
                .build();
        pricingRepository.save(baseRule);

        LocalDate futureDate = LocalDate.now().plusDays(4);

        // User 1 creates and pays booking
        ResourceBookingRequest req = new ResourceBookingRequest();
        req.setResourceId(gymStudio.getId());
        req.setBookingDate(futureDate.toString());
        req.setStartTime("08:00:00");
        req.setEndTime("09:00:00");
        req.setPurpose("Yoga Session");
        ResourceBookingResponse booking = bookingService.createBooking(req, residentUser, testCommunity);

        // Mark as PAID
        ResourceBooking bookingEntity = bookingRepository.findById(booking.getId()).orElseThrow();
        bookingEntity.setPaymentStatus(PaymentStatus.PAID);
        bookingRepository.save(bookingEntity);

        // User 2 joins waitlist for that slot
        WaitlistRequest waitlistReq = new WaitlistRequest();
        waitlistReq.setResourceId(gymStudio.getId());
        waitlistReq.setRequestedDate(futureDate.toString());
        waitlistReq.setRequestedStartTime("08:00:00");
        waitlistReq.setRequestedEndTime("09:00:00");
        WaitlistResponse waitlistEntry = waitlistService.joinWaitlist(waitlistReq, secondUser, testCommunity);
        assertEquals(WaitlistStatus.WAITING, waitlistEntry.getStatus());

        // Cancel booking >24 hours prior
        ResourceBookingResponse cancelled = bookingService.cancelBooking(booking.getId(), residentUser.getId(), "Travel plans changed");
        assertEquals(BookingStatus.CANCELLED.name(), cancelled.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(cancelled.getRefundAmount()));
        assertEquals(PaymentStatus.REFUNDED.name(), cancelled.getPaymentStatus());

        // Check waitlist candidate was automatically notified
        BookingWaitlist updatedWaitlist = waitlistRepository.findById(waitlistEntry.getId()).orElseThrow();
        assertEquals(WaitlistStatus.NOTIFIED, updatedWaitlist.getStatus());
        assertNotNull(updatedWaitlist.getNotifiedAt());
    }

    @Test
    @DisplayName("6. No-show penalty applied to unfulfilled booking")
    void testNoShowPenalty() {
        Resource squashCourt = Resource.builder()
                .name("Squash Court")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(2)
                .openTime(LocalTime.of(6, 0))
                .closeTime(LocalTime.of(22, 0))
                .status(ResourceStatus.ACTIVE)
                .build();
        squashCourt = resourceRepository.save(squashCourt);

        LocalDate date = LocalDate.now().plusDays(1);
        ResourceBookingRequest req = new ResourceBookingRequest();
        req.setResourceId(squashCourt.getId());
        req.setBookingDate(date.toString());
        req.setStartTime("07:00:00");
        req.setEndTime("08:00:00");
        req.setPurpose("Morning game");
        ResourceBookingResponse booking = bookingService.createBooking(req, residentUser, testCommunity);

        // Mark as NO_SHOW with penalty fee
        ResourceBookingResponse noShowResponse = bookingService.markNoShow(
                booking.getId(), new BigDecimal("150.00"), "Resident did not check in within 15 minutes");

        assertEquals(BookingStatus.NO_SHOW.name(), noShowResponse.getStatus());
        assertEquals(0, new BigDecimal("150.00").compareTo(noShowResponse.getPenaltyAmount()));
    }

    @Test
    @DisplayName("7. Recurring booking generates recurring slot occurrences")
    void testRecurringBookings() {
        Resource gym = Resource.builder()
                .name("Community Gymnasium")
                .category(clubhouseCategory)
                .community(testCommunity)
                .capacity(30)
                .openTime(LocalTime.of(6, 0))
                .closeTime(LocalTime.of(22, 0))
                .status(ResourceStatus.ACTIVE)
                .recurringBookingAllowed(true)
                .build();
        gym = resourceRepository.save(gym);

        LocalDate date = LocalDate.now().plusDays(1);
        ResourceBookingRequest req = new ResourceBookingRequest();
        req.setResourceId(gym.getId());
        req.setBookingDate(date.toString());
        req.setStartTime("06:00:00");
        req.setEndTime("07:00:00");
        req.setPurpose("Daily morning fitness");
        req.setIsRecurring(true);
        req.setRecurringOccurrences(3);
        req.setRecurringFrequency("DAILY");

        ResourceBookingResponse parent = bookingService.createBooking(req, residentUser, testCommunity);
        assertTrue(parent.getIsRecurring());
        assertEquals("DAILY", parent.getRecurringPattern());

        List<ResourceBookingResponse> myBookings = bookingService.getMyBookings(residentUser.getId(), null);
        assertTrue(myBookings.size() >= 3);
    }
}
