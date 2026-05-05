package org.jackfruit.keliri.service;

import org.jackfruit.keliri.model.AdminDashboardResponse;
import org.jackfruit.keliri.model.ad_campaigns;
import org.jackfruit.keliri.model.hitRecord;
import org.jackfruit.keliri.repository.PublisherRepository;
import org.jackfruit.keliri.repository.ad_campaignsRepository;
import org.jackfruit.keliri.repository.hitRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    @Autowired
    private ad_campaignsRepository campaignsRepository;

    @Autowired
    private hitRecordRepository hitRecordRepository;

    @Autowired
    private PublisherRepository publisherRepository;

    @Autowired
    private org.jackfruit.keliri.repository.PaymentTransactionRepository transactionRepository;

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public AdminDashboardResponse getDashboardData(String adminId) {

        // 1. Fetch related ad campaigns
        List<ad_campaigns> campaigns = campaignsRepository.findByCreatedBy(adminId);
        List<String> campaignIds = campaigns.stream()
                .map(ad_campaigns::getId)
                .collect(Collectors.toList());

        // 2. Fetch hit records for 30 days
        Instant thirtyDaysAgoInstant = Instant.now().minus(30, ChronoUnit.DAYS);
        System.out.println("====== SCOPED DASHBOARD DATA FOR " + adminId + " ======");

        // 1. Get Ads/Campaigns count from local repository (filtered by createdBy)
        long totalAds = campaigns.size();
        long activeAds = campaigns.stream().filter(c -> "ACTIVE".equalsIgnoreCase(c.getCompaignsStatus())).count();
        long expiredAds = campaigns.stream().filter(c -> "EXPIRED".equalsIgnoreCase(c.getCompaignsStatus())).count();

        // 2. Get Publishers count
        // For local publishers (Atlas)
        long localPublishers = publisherRepository.findByAdminId(adminId).size();

        // Return 1 if at least one exists (matching user expectation for k@gmail.com)
        // Or keep it dynamic. Let's see if we can identify the admin's company email
        long totalPublishers = localPublishers;

        System.out.println("Found " + totalAds + " ads and " + totalPublishers + " publishers.");

        Date thirtyDaysAgoDate = Date.from(thirtyDaysAgoInstant);

        List<hitRecord> hits = new ArrayList<>();
        if (!campaignIds.isEmpty()) {
            hits = hitRecordRepository.findByCampaignIdInAndTimestampAfter(campaignIds, thirtyDaysAgoDate);
        }

        // 3. Compute KPIs
        System.out.println("====== DASHBOARD DATA FOR " + adminId + " ======");
        System.out.println("Total Ads: " + totalAds);
        System.out.println("Active Ads: " + activeAds);
        System.out.println("Total Publishers: " + totalPublishers);

        // Fetch successful transactions to compute actual total spend
        List<org.jackfruit.keliri.model.PaymentTransaction> transactions = transactionRepository
                .findByAdminIdAndStatus(adminId, "SUCCESS");
        double actualSpend = 0.0;
        if (transactions != null) {
            for (org.jackfruit.keliri.model.PaymentTransaction txn : transactions) {
                actualSpend += txn.getAmount();
            }
        }
        long totalSpend = Math.round(actualSpend);

        long totalClicks = hits.stream().filter(h -> "AD_CLICK".equalsIgnoreCase(h.getEventType())).count();

        // 4. Group chart data
        Map<String, AdminDashboardResponse.ChartData> dayDataMap = new LinkedHashMap<>();

        // Initialize last 30 days
        LocalDate today = LocalDate.now(ZONE_ID);
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String dateStr = d.format(DATE_FORMATTER);
            AdminDashboardResponse.ChartData chartData = new AdminDashboardResponse.ChartData();
            chartData.setDate(dateStr);
            chartData.setClicks(0);
            chartData.setImpressions(0);
            chartData.setSpend(0);
            dayDataMap.put(dateStr, chartData);
        }

        // Map hits over timeline
        for (hitRecord hit : hits) {
            String hitDate = hit.getTimestamp().toInstant().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
            if (dayDataMap.containsKey(hitDate)) {
                AdminDashboardResponse.ChartData dayData = dayDataMap.get(hitDate);
                if ("AD_CLICK".equalsIgnoreCase(hit.getEventType())) {
                    dayData.setClicks(dayData.getClicks() + 1);
                } else if ("AD_VIEW".equalsIgnoreCase(hit.getEventType())) {
                    dayData.setImpressions(dayData.getImpressions() + 1);
                }
            }
        }

        // Calculate mapping of actual transactions to the timeline
        if (transactions != null) {
            for (org.jackfruit.keliri.model.PaymentTransaction txn : transactions) {
                if (txn.getCreatedAt() != null) {
                    String txnDate = txn.getCreatedAt().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
                    if (dayDataMap.containsKey(txnDate)) {
                        AdminDashboardResponse.ChartData dayData = dayDataMap.get(txnDate);
                        dayData.setSpend(dayData.getSpend() + Math.round(txn.getAmount()));
                    }
                }
            }
        }

        List<AdminDashboardResponse.ChartData> allCharts = new ArrayList<>(dayDataMap.values());

        // 5. Construct Response
        AdminDashboardResponse response = new AdminDashboardResponse();
        response.setTotalAds(totalAds);
        response.setActiveAds(activeAds);
        response.setExpiredAds(expiredAds);
        response.setTotalPublishers(totalPublishers);
        response.setTotalSpend(totalSpend);
        response.setTotalClicks(totalClicks);

        response.setPerformanceTrend(allCharts);
        response.setEngagementTrend(allCharts);
        response.setSpendVsPerformance(allCharts);

        return response;
    }
}
