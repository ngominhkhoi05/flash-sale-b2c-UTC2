package com.b2c.flash_sale_b2c_UTC2.dashboard.service;

import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardCategoryShareDto;
import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardChartPointDto;
import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardPendingTasksDto;
import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardTopStoreDto;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletWithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> SUCCESS_ORDER_STATUSES = Arrays.asList(
            "CONFIRMED", "SHIPPED", "DELIVERED", "COMPLETED", "PAID"
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;
    private final WalletWithdrawalRequestRepository walletWithdrawalRequestRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary(
            String periodType,
            Integer year,
            Integer month,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate
    ) {
        String normalizedType = (periodType != null) ? periodType.toUpperCase() : "MONTH";
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        Instant startInstant;
        Instant endInstant;
        Instant prevStartInstant;
        Instant prevEndInstant;
        String periodLabel;

        switch (normalizedType) {
            case "DAY": {
                LocalDate targetDate = (date != null) ? date : today;
                startInstant = targetDate.atStartOfDay(VIETNAM_ZONE).toInstant();
                endInstant = targetDate.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
                prevStartInstant = targetDate.minusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
                prevEndInstant = startInstant;
                periodLabel = "Ngày " + targetDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                break;
            }
            case "YEAR": {
                int targetYear = (year != null && year > 2000) ? year : today.getYear();
                startInstant = LocalDate.of(targetYear, 1, 1).atStartOfDay(VIETNAM_ZONE).toInstant();
                endInstant = LocalDate.of(targetYear + 1, 1, 1).atStartOfDay(VIETNAM_ZONE).toInstant();
                prevStartInstant = LocalDate.of(targetYear - 1, 1, 1).atStartOfDay(VIETNAM_ZONE).toInstant();
                prevEndInstant = startInstant;
                periodLabel = "Năm " + targetYear;
                break;
            }
            case "CUSTOM": {
                LocalDate sDate = (startDate != null) ? startDate : today.minusDays(30);
                LocalDate eDate = (endDate != null) ? endDate : today;
                if (sDate.isAfter(eDate)) {
                    LocalDate tmp = sDate;
                    sDate = eDate;
                    eDate = tmp;
                }
                startInstant = sDate.atStartOfDay(VIETNAM_ZONE).toInstant();
                endInstant = eDate.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
                long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate) + 1;
                prevStartInstant = sDate.minusDays(daysDiff).atStartOfDay(VIETNAM_ZONE).toInstant();
                prevEndInstant = startInstant;
                periodLabel = sDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - " + eDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                break;
            }
            case "MONTH":
            default: {
                normalizedType = "MONTH";
                int targetYear = (year != null && year > 2000) ? year : today.getYear();
                int targetMonth = (month != null && month >= 1 && month <= 12) ? month : today.getMonthValue();
                YearMonth ym = YearMonth.of(targetYear, targetMonth);
                startInstant = ym.atDay(1).atStartOfDay(VIETNAM_ZONE).toInstant();
                endInstant = ym.plusMonths(1).atDay(1).atStartOfDay(VIETNAM_ZONE).toInstant();
                YearMonth prevYm = ym.minusMonths(1);
                prevStartInstant = prevYm.atDay(1).atStartOfDay(VIETNAM_ZONE).toInstant();
                prevEndInstant = startInstant;
                periodLabel = "Tháng " + String.format("%02d", targetMonth) + "/" + targetYear;
                break;
            }
        }

        // 1. KPI Doanh thu (GMV)
        BigDecimal totalRevenue = orderRepository.sumTotalAmountByCreatedAtBetweenAndStatusIn(
                startInstant, endInstant, SUCCESS_ORDER_STATUSES
        );
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal prevRevenue = orderRepository.sumTotalAmountByCreatedAtBetweenAndStatusIn(
                prevStartInstant, prevEndInstant, SUCCESS_ORDER_STATUSES
        );
        if (prevRevenue == null) prevRevenue = BigDecimal.ZERO;
        Double revenueGrowthPercent = calculateGrowth(totalRevenue.doubleValue(), prevRevenue.doubleValue());

        // 2. KPI Đơn hàng
        long totalOrders = orderRepository.countByCreatedAtBetweenAndStatusIn(
                startInstant, endInstant, SUCCESS_ORDER_STATUSES
        );
        long prevOrders = orderRepository.countByCreatedAtBetweenAndStatusIn(
                prevStartInstant, prevEndInstant, SUCCESS_ORDER_STATUSES
        );
        Double ordersGrowthPercent = calculateGrowth((double) totalOrders, (double) prevOrders);

        // 3. KPI Người dùng mới
        long newUsers = userRepository.countByCreatedAtBetween(startInstant, endInstant);
        long prevUsers = userRepository.countByCreatedAtBetween(prevStartInstant, prevEndInstant);
        Double usersGrowthPercent = calculateGrowth((double) newUsers, (double) prevUsers);

        // 4. KPI Gian hàng mới
        long newStores = storeRepository.countByCreatedAtBetween(startInstant, endInstant);
        long prevStores = storeRepository.countByCreatedAtBetween(prevStartInstant, prevEndInstant);
        Double storesGrowthPercent = calculateGrowth((double) newStores, (double) prevStores);

        // 5. Chuỗi dữ liệu biểu đồ
        List<AdminDashboardChartPointDto> chartPoints = buildChartPoints(
                normalizedType, startInstant, endInstant, year, month, date
        );

        // 6. Top Gian hàng doanh thu cao nhất trong kỳ
        List<Object[]> topStoreRows = orderRepository.findTopStoresByRevenue(
                startInstant, endInstant, SUCCESS_ORDER_STATUSES, PageRequest.of(0, 5)
        );
        List<AdminDashboardTopStoreDto> topStores = new ArrayList<>();
        for (Object[] row : topStoreRows) {
            Long storeId = ((Number) row[0]).longValue();
            String storeName = (String) row[1];
            String ownerName = (String) row[2];
            BigDecimal revenue = (row[3] instanceof BigDecimal) ? (BigDecimal) row[3] : BigDecimal.valueOf(((Number) row[3]).doubleValue());
            Long orderCount = ((Number) row[4]).longValue();

            topStores.add(AdminDashboardTopStoreDto.builder()
                    .id(storeId)
                    .name(storeName)
                    .legalName(ownerName != null ? ownerName : storeName)
                    .revenue(revenue)
                    .orders(orderCount)
                    .avatarUrl("https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?w=120&auto=format&fit=crop&q=80")
                    .build());
        }

        // 7. Doanh thu theo cơ cấu ngành hàng
        List<Object[]> catRows = orderRepository.findCategoryRevenueBreakdown(
                startInstant, endInstant, SUCCESS_ORDER_STATUSES
        );
        List<AdminDashboardCategoryShareDto> categoryShares = new ArrayList<>();
        BigDecimal catTotal = BigDecimal.ZERO;
        for (Object[] row : catRows) {
            BigDecimal rev = (row[2] instanceof BigDecimal) ? (BigDecimal) row[2] : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            catTotal = catTotal.add(rev);
        }

        for (Object[] row : catRows) {
            Long catId = ((Number) row[0]).longValue();
            String catName = (String) row[1];
            BigDecimal rev = (row[2] instanceof BigDecimal) ? (BigDecimal) row[2] : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            double share = 0.0;
            if (catTotal.compareTo(BigDecimal.ZERO) > 0) {
                share = rev.multiply(BigDecimal.valueOf(100))
                        .divide(catTotal, 1, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            categoryShares.add(AdminDashboardCategoryShareDto.builder()
                    .categoryId(catId)
                    .name(catName)
                    .revenue(rev)
                    .sharePercentage(share)
                    .build());
        }

        // Nếu chưa có đơn hàng phân loại, lấy danh mục mặc định để hiển thị
        if (categoryShares.isEmpty()) {
            categoryRepository.findAll().stream().limit(6).forEach(c -> {
                categoryShares.add(AdminDashboardCategoryShareDto.builder()
                        .categoryId(c.getId() != null ? c.getId().longValue() : null)
                        .name(c.getName())
                        .revenue(BigDecimal.ZERO)
                        .sharePercentage(0.0)
                        .build());
            });
        }

        // 8. Nhiệm vụ chờ xử lý
        long pendingStoresCount = storeRepository.countByStatus("PENDING");
        long pendingFsItemsCount = flashSaleItemRepository.countByStatus("PENDING");
        long pendingWithdrawalsCount = walletWithdrawalRequestRepository.countByStatus("PENDING");

        AdminDashboardPendingTasksDto pendingTasks = AdminDashboardPendingTasksDto.builder()
                .pendingStores(pendingStoresCount)
                .pendingFlashSaleItems(pendingFsItemsCount)
                .pendingWithdrawals(pendingWithdrawalsCount)
                .reportedReviews(0L)
                .build();

        return AdminDashboardSummaryResponse.builder()
                .periodType(normalizedType)
                .periodLabel(periodLabel)
                .startDate(startInstant)
                .endDate(endInstant)
                .totalRevenue(totalRevenue)
                .revenueGrowthPercent(revenueGrowthPercent)
                .totalOrders(totalOrders)
                .ordersGrowthPercent(ordersGrowthPercent)
                .newUsers(newUsers)
                .usersGrowthPercent(usersGrowthPercent)
                .newStores(newStores)
                .storesGrowthPercent(storesGrowthPercent)
                .chartPoints(chartPoints)
                .topStores(topStores)
                .categoryShares(categoryShares)
                .pendingTasks(pendingTasks)
                .build();
    }

    private Double calculateGrowth(double current, double previous) {
        if (previous <= 0.0) {
            return (current > 0.0) ? 100.0 : 0.0;
        }
        double pct = ((current - previous) / previous) * 100.0;
        return Math.round(pct * 10.0) / 10.0;
    }

    private List<AdminDashboardChartPointDto> buildChartPoints(
            String periodType,
            Instant startInstant,
            Instant endInstant,
            Integer year,
            Integer month,
            LocalDate date
    ) {
        List<Order> orders = orderRepository.findOrdersForDashboard(
                startInstant, endInstant, SUCCESS_ORDER_STATUSES
        );

        Map<String, AdminDashboardChartPointDto> map = new LinkedHashMap<>();

        if ("DAY".equalsIgnoreCase(periodType)) {
            // Nhóm theo 6 khung giờ: 00h-04h, 04h-08h, 08h-12h, 12h-16h, 16h-20h, 20h-24h
            String[] slots = {"00:00 - 04:00", "04:00 - 08:00", "08:00 - 12:00", "12:00 - 16:00", "16:00 - 20:00", "20:00 - 24:00"};
            for (String s : slots) {
                map.put(s, new AdminDashboardChartPointDto(s, BigDecimal.ZERO, 0L));
            }
            for (Order o : orders) {
                if (o.getCreatedAt() == null) continue;
                ZonedDateTime zdt = o.getCreatedAt().atZone(VIETNAM_ZONE);
                int h = zdt.getHour();
                int slotIdx = Math.min(h / 4, 5);
                String slotKey = slots[slotIdx];
                AdminDashboardChartPointDto pt = map.get(slotKey);
                if (pt != null) {
                    pt.setRevenue(pt.getRevenue().add(o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO));
                    pt.setOrders(pt.getOrders() + 1);
                }
            }
        } else if ("YEAR".equalsIgnoreCase(periodType)) {
            // Nhóm theo 12 tháng: T1 .. T12
            for (int m = 1; m <= 12; m++) {
                String label = "Tháng " + m;
                map.put(label, new AdminDashboardChartPointDto(label, BigDecimal.ZERO, 0L));
            }
            for (Order o : orders) {
                if (o.getCreatedAt() == null) continue;
                ZonedDateTime zdt = o.getCreatedAt().atZone(VIETNAM_ZONE);
                String label = "Tháng " + zdt.getMonthValue();
                AdminDashboardChartPointDto pt = map.get(label);
                if (pt != null) {
                    pt.setRevenue(pt.getRevenue().add(o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO));
                    pt.setOrders(pt.getOrders() + 1);
                }
            }
        } else {
            // Mặc định MONTH (hoặc CUSTOM): Nhóm theo từng ngày trong tháng
            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            int targetYear = (year != null && year > 2000) ? year : today.getYear();
            int targetMonth = (month != null && month >= 1 && month <= 12) ? month : today.getMonthValue();
            YearMonth ym = YearMonth.of(targetYear, targetMonth);
            int days = ym.lengthOfMonth();

            for (int d = 1; d <= days; d++) {
                String label = String.format("%02d/%02d", d, targetMonth);
                map.put(label, new AdminDashboardChartPointDto(label, BigDecimal.ZERO, 0L));
            }

            for (Order o : orders) {
                if (o.getCreatedAt() == null) continue;
                ZonedDateTime zdt = o.getCreatedAt().atZone(VIETNAM_ZONE);
                String label = String.format("%02d/%02d", zdt.getDayOfMonth(), zdt.getMonthValue());
                AdminDashboardChartPointDto pt = map.get(label);
                if (pt != null) {
                    pt.setRevenue(pt.getRevenue().add(o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO));
                    pt.setOrders(pt.getOrders() + 1);
                }
            }
        }

        return new ArrayList<>(map.values());
    }
}
