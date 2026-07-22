package com.paguelofacil.adquirencia.service;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.Merchant;
import com.paguelofacil.adquirencia.domain.Processor;
import com.paguelofacil.adquirencia.domain.ServiceType;
import com.paguelofacil.adquirencia.domain.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final DataStore store;

    public DashboardService(DataStore store) {
        this.store = store;
    }

    // ---------- DTOs ----------

    public record Range(LocalDate from, LocalDate to) {}

    public record VolumeKpi(BigDecimal total, BigDecimal previous, Double deltaPct) {}

    public record TxKpi(long total, long approved, long declined, Double approvalRate) {}

    public record RefundKpi(long count, BigDecimal amount, Double pctOfVolume) {}

    public record ProcessorSlice(String processor, String label, BigDecimal volume, long txCount,
                                 Double approvalRate, Double sharePct,
                                 BigDecimal previousVolume, long previousTxCount, Double deltaPct) {}

    public record ServiceSlice(String service, String label, BigDecimal volume, long txCount, Double sharePct) {}

    public record ThreeDsKpi(long withCount, Double withApprovalRate,
                             long withoutCount, Double withoutApprovalRate, Double withSharePct) {}

    public record DailyPoint(LocalDate date, BigDecimal volume, long txCount, long approved, long declined) {}

    public record DrCodeCount(String code, String description, String action, long count, Double pctOfDeclined) {}

    public record Summary(Range range, Range previousRange, VolumeKpi volume, TxKpi tx, RefundKpi refunds,
                          List<ProcessorSlice> processors, List<ServiceSlice> services, ThreeDsKpi threeDs,
                          List<DailyPoint> daily, List<DrCodeCount> topDeclineCodes) {}

    public record MerchantComparison(String merchantId, String merchantName, BigDecimal volume,
                                     long txCount, Double approvalRate, Double refundPct) {}

    // ---------- API ----------

    public Summary summary(String merchantId, LocalDate from, LocalDate to, ServiceType service) {
        LocalDate today = LocalDate.now();
        if (to == null) to = today;
        if (from == null) from = to.minusDays(29);
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }

        long days = from.until(to).getDays() + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        List<Transaction> current = filter(merchantId, from, to, service);
        List<Transaction> previous = filter(merchantId, prevFrom, prevTo, service);

        List<Transaction> sales = current.stream().filter(t -> t.type() == Transaction.TxType.SALE).toList();
        List<Transaction> approvedSales = sales.stream().filter(Transaction::approved).toList();
        List<Transaction> refunds = current.stream().filter(t -> t.type() == Transaction.TxType.REFUND).toList();

        BigDecimal volume = sum(approvedSales);
        BigDecimal prevVolume = sum(previous.stream()
                .filter(t -> t.type() == Transaction.TxType.SALE && t.approved()).toList());
        Double deltaPct = prevVolume.signum() == 0 ? null
                : round1(volume.subtract(prevVolume).multiply(BigDecimal.valueOf(100))
                        .divide(prevVolume, 4, RoundingMode.HALF_UP).doubleValue());

        long declined = sales.size() - approvedSales.size();
        TxKpi txKpi = new TxKpi(sales.size(), approvedSales.size(), declined,
                rate(approvedSales.size(), sales.size()));

        BigDecimal refundAmount = sum(refunds);
        RefundKpi refundKpi = new RefundKpi(refunds.size(), refundAmount,
                volume.signum() == 0 ? null
                        : round1(refundAmount.multiply(BigDecimal.valueOf(100))
                                .divide(volume, 4, RoundingMode.HALF_UP).doubleValue()));

        // Por procesador (con comparativa contra el periodo anterior)
        List<Transaction> prevSales = previous.stream()
                .filter(t -> t.type() == Transaction.TxType.SALE).toList();
        List<ProcessorSlice> processors = new ArrayList<>();
        Map<Processor, List<Transaction>> byProc = new EnumMap<>(Processor.class);
        Map<Processor, List<Transaction>> byProcPrev = new EnumMap<>(Processor.class);
        for (Processor p : Processor.values()) {
            byProc.put(p, new ArrayList<>());
            byProcPrev.put(p, new ArrayList<>());
        }
        sales.forEach(t -> byProc.get(t.processor()).add(t));
        prevSales.forEach(t -> byProcPrev.get(t.processor()).add(t));
        for (Processor p : Processor.values()) {
            List<Transaction> list = byProc.get(p);
            List<Transaction> prevList = byProcPrev.get(p);
            long ok = list.stream().filter(Transaction::approved).count();
            BigDecimal vol = sum(list.stream().filter(Transaction::approved).toList());
            BigDecimal prevVol = sum(prevList.stream().filter(Transaction::approved).toList());
            Double procDelta = prevVol.signum() == 0 ? null
                    : round1(vol.subtract(prevVol).multiply(BigDecimal.valueOf(100))
                            .divide(prevVol, 4, RoundingMode.HALF_UP).doubleValue());
            processors.add(new ProcessorSlice(p.name(), p.getLabel(), vol, list.size(),
                    rate(ok, list.size()),
                    volume.signum() == 0 ? null
                            : round1(vol.multiply(BigDecimal.valueOf(100))
                                    .divide(volume, 4, RoundingMode.HALF_UP).doubleValue()),
                    prevVol, prevList.size(), procDelta));
        }

        // Por servicio
        List<ServiceSlice> services = new ArrayList<>();
        for (ServiceType s : ServiceType.values()) {
            List<Transaction> list = sales.stream().filter(t -> t.serviceType() == s).toList();
            BigDecimal vol = sum(list.stream().filter(Transaction::approved).toList());
            services.add(new ServiceSlice(s.name(), s.getLabel(), vol, list.size(),
                    volume.signum() == 0 ? null
                            : round1(vol.multiply(BigDecimal.valueOf(100))
                                    .divide(volume, 4, RoundingMode.HALF_UP).doubleValue())));
        }
        services.sort(Comparator.comparing(ServiceSlice::volume).reversed());

        // 3DS vs sin 3DS
        List<Transaction> with3ds = sales.stream().filter(Transaction::threeDs).toList();
        List<Transaction> without3ds = sales.stream().filter(t -> !t.threeDs()).toList();
        ThreeDsKpi threeDs = new ThreeDsKpi(
                with3ds.size(), rate(with3ds.stream().filter(Transaction::approved).count(), with3ds.size()),
                without3ds.size(), rate(without3ds.stream().filter(Transaction::approved).count(), without3ds.size()),
                rate(with3ds.size(), sales.size()));

        // Serie diaria
        Map<LocalDate, List<Transaction>> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) byDay.put(d, new ArrayList<>());
        sales.forEach(t -> {
            List<Transaction> list = byDay.get(t.timestamp().toLocalDate());
            if (list != null) list.add(t);
        });
        List<DailyPoint> daily = new ArrayList<>();
        byDay.forEach((d, list) -> {
            long ok = list.stream().filter(Transaction::approved).count();
            daily.add(new DailyPoint(d, sum(list.stream().filter(Transaction::approved).toList()),
                    list.size(), ok, list.size() - ok));
        });

        // Top códigos DR
        Map<String, Long> codeCounts = new LinkedHashMap<>();
        sales.stream().filter(t -> !t.approved() && t.drCode() != null)
                .forEach(t -> codeCounts.merge(t.drCode(), 1L, Long::sum));
        long totalDeclined = codeCounts.values().stream().mapToLong(Long::longValue).sum();
        List<DrCodeCount> topCodes = codeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(e -> new DrCodeCount(e.getKey(),
                        DataStore.DR_CODES.getOrDefault(e.getKey(), "Código de rechazo"),
                        DataStore.DR_ACTIONS.getOrDefault(e.getKey(), DataStore.ACT_PF),
                        e.getValue(), rate(e.getValue(), totalDeclined)))
                .toList();

        return new Summary(new Range(from, to), new Range(prevFrom, prevTo),
                new VolumeKpi(volume, prevVolume, deltaPct), txKpi, refundKpi,
                processors, services, threeDs, daily, topCodes);
    }

    public List<MerchantComparison> compare(List<String> merchantIds, LocalDate from, LocalDate to,
                                            ServiceType service) {
        List<MerchantComparison> out = new ArrayList<>();
        for (String id : merchantIds) {
            Merchant m = store.merchants().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
            if (m == null) continue;
            Summary s = summary(id, from, to, service);
            out.add(new MerchantComparison(m.id(), m.name(), s.volume().total(),
                    s.tx().total(), s.tx().approvalRate(), s.refunds().pctOfVolume()));
        }
        out.sort(Comparator.comparing(MerchantComparison::volume).reversed());
        return out;
    }

    // ---------- helpers ----------

    private List<Transaction> filter(String merchantId, LocalDate from, LocalDate to, ServiceType service) {
        return store.transactions().stream()
                .filter(t -> merchantId == null || t.merchantId().equals(merchantId))
                .filter(t -> service == null || t.serviceType() == service)
                .filter(t -> {
                    LocalDate d = t.timestamp().toLocalDate();
                    return !d.isBefore(from) && !d.isAfter(to);
                })
                .toList();
    }

    private static BigDecimal sum(List<Transaction> list) {
        return list.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double rate(long part, long total) {
        return total == 0 ? null : round1(part * 100.0 / total);
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
