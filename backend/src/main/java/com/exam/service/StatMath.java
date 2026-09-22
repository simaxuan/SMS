package com.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯统计计算工具（无 IO、无权限，仅做数值聚合），供 {@link StatisticsService} 复用。
 * <p>从原 {@code StatisticsService} 剥离的辅助方法集中于本类，实现：
 * <ul>
 *   <li>分数段分布、班级/范围聚合、名次赋值</li>
 *   <li>标准分加权（equal / full-weighted / custom-weighted）</li>
 *   <li>分数/满分/百分比换算与统一四舍五入</li>
 * </ul>
 * <p>统计口径规则（及格线=满分×60%、优秀线=满分×85%、分数段按得分率划分）见
 * {@link StatisticsService} 的类注释，本类数值逻辑与其保持一致。
 */
@Component
public class StatMath {

    private static final double PASS_RATE_LINE = 0.60;      // 及格线 = 满分 × 60%
    private static final double EXCELLENT_RATE_LINE = 0.85; // 优秀线 = 满分 × 85%
    private static final String[] BUCKETS = {"0-59%", "60-69%", "70-79%", "80-89%", "90-100%"};

    /** 解析 std_weights JSON 用（无状态，单例复用）。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SettingsService settingsService;

    public StatMath(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 构造单一成绩项（原始分/满分/得分率%）。 */
    public ScoreItem item(int score, int fullScore) {
        int full = fullScore <= 0 ? 100 : fullScore;
        double raw = score * 100.0 / full;
        // percent 用于展示（四舍五入 1 位）；rawPercent 用于及格/优秀/分数段判定，
        // 避免边界 [59.95,60)、[84.95,85) 因四舍五入到 60/85 而被误判及格/优秀。
        return new ScoreItem(score, full, round1(raw), raw);
    }

    /** 得分率是否达及格线（60%）。基于原始得分率判定，不受展示四舍五入影响。 */
    public boolean isPass(ScoreItem it) {
        return it.rawPercent >= PASS_RATE_LINE * 100;
    }

    /** 得分率是否达优秀线（85%）。基于原始得分率判定，不受展示四舍五入影响。 */
    public boolean isExcellent(ScoreItem it) {
        return it.rawPercent >= EXCELLENT_RATE_LINE * 100;
    }

    /** 分数段分布：<60 / 60-69 / 70-79 / 80-89 / ≥90。 */
    public Map<String, Integer> distribution(List<ScoreItem> items) {
        Map<String, Integer> dist = new HashMap<>();
        for (String b : BUCKETS) dist.put(b, 0);
        for (ScoreItem it : items) {
            double p = it.rawPercent; // 分数段基于原始得分率判定，避免边界四舍五入误判
            if (p < 60) dist.merge(BUCKETS[0], 1, Integer::sum);
            else if (p < 70) dist.merge(BUCKETS[1], 1, Integer::sum);
            else if (p < 80) dist.merge(BUCKETS[2], 1, Integer::sum);
            else if (p < 90) dist.merge(BUCKETS[3], 1, Integer::sum);
            else dist.merge(BUCKETS[4], 1, Integer::sum);
        }
        return dist;
    }

    /**
     * 对成绩行聚合（RankRow 投影：score/fullScore 由具名字段读取，消除 Object[] 魔法列序）。
     * 用于班级对比、总体对照。
     */
    public Map<String, Object> aggregate(List<com.exam.dto.RankRow> rows) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("total", 0);
            result.put("avg", 0);
            result.put("percentAvg", 0);
            result.put("passRate", 0);
            result.put("excellentRate", 0);
            result.put("distribution", distribution(new ArrayList<>()));
            return result;
        }
        List<ScoreItem> items = new ArrayList<>();
        for (com.exam.dto.RankRow r : rows) {
            int score = r.score() == null ? 0 : r.score();
            int full = r.safeFullScore();
            double raw = score * 100.0 / full;
            items.add(new ScoreItem(score, full, round1(raw), raw));
        }
        int total = items.size();
        int pass = 0, excellent = 0;
        double sum = 0, percentSum = 0;
        for (ScoreItem it : items) {
            if (isPass(it)) pass++;
            if (isExcellent(it)) excellent++;
            sum += it.score;
            percentSum += it.percent;
        }
        result.put("total", total);
        result.put("avg", BigDecimal.valueOf(sum / total).setScale(2, RoundingMode.HALF_UP));
        result.put("percentAvg", BigDecimal.valueOf(percentSum / total).setScale(2, RoundingMode.HALF_UP));
        result.put("passRate", percent(pass, total));
        result.put("excellentRate", percent(excellent, total));
        result.put("distribution", distribution(items));
        return result;
    }

    /** 按当前已排序列表赋名次：ties=true 标准并列(1,2,2,4)；false 唯一顺延(1,2,3,4)。 */
    public void assignRank(List<Map<String, Object>> sorted, boolean ties) {
        int n = sorted.size();
        long prevRank = 0;
        for (int i = 0; i < n; i++) {
            if (ties && i > 0 && sorted.get(i).get("score").equals(sorted.get(i - 1).get("score"))) {
                sorted.get(i).put("rank", prevRank);
            } else {
                prevRank = (long) i + 1;
                sorted.get(i).put("rank", i + 1);
            }
        }
    }

    /**
     * 标准分（得分率加权/等权）。权重模式来自设置 std_weight_mode：
     * - equal：各科得分率等权均值（默认）；
     * - full-weighted：Σ(得分率×满分)/Σ满分（即按满分加权的得分率）；
     * - custom-weighted：Σ(得分率×用户权重)/Σ用户权重，权重取自 std_weights JSON({courseId:权重})；
     *   解析失败回退 equal。
     */
    public double weightedStdScore(List<StdItem> items) {
        if (items == null || items.isEmpty()) return 0;
        String mode = settingsService.getString(SettingsService.KEY_STD_WEIGHT_MODE, "equal");
        if ("full-weighted".equals(mode)) {
            double num = 0, den = 0;
            for (StdItem it : items) {
                num += it.percent * it.fullScore;
                den += it.fullScore;
            }
            return den > 0 ? round1(num / den) : 0;
        }
        if ("custom-weighted".equals(mode)) {
            Map<Long, Double> weights = parseStdWeights();
            double num = 0, den = 0;
            for (StdItem it : items) {
                double w = weights.getOrDefault(it.courseId, 1.0);
                num += it.percent * w;
                den += w;
            }
            return den > 0 ? round1(num / den) : 0;
        }
        double sum = 0;
        for (StdItem it : items) sum += it.percent;
        return round1(sum / items.size());
    }

    /** 解析 std_weights JSON 为 {courseId:权重}；解析失败返回空映射（回退 equal）。 */
    private Map<Long, Double> parseStdWeights() {
        Map<Long, Double> map = new HashMap<>();
        String json = settingsService.getString(SettingsService.KEY_STD_WEIGHTS, "{}");
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(e -> {
                    try {
                        long cid = Long.parseLong(e.getKey());
                        double w = e.getValue().asDouble(1.0);
                        if (w > 0) map.put(cid, w);
                    } catch (Exception ignored) {
                        // 单条非法跳过
                    }
                });
            }
        } catch (Exception ignored) {
            // 整体解析失败回退 equal
        }
        return map;
    }

    public BigDecimal percent(int num, int den) {
        if (den == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(num * 100.0 / den).setScale(2, RoundingMode.HALF_UP);
    }

    public double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof char[] c) return new BigDecimal(new String(c));
        return new BigDecimal(String.valueOf(value));
    }

    public Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number num) return num.longValue();
        return Long.valueOf(String.valueOf(o));
    }

    /** 单项：原始分/满分/得分率%。 */
    public static class ScoreItem {
        public final int score;
        public final int fullScore;
        public final double percent;     // 展示用：得分率四舍五入到 1 位
        public final double rawPercent;  // 判定用：原始得分率（未四舍五入），用于及格/优秀/分数段阈值判定

        ScoreItem(int score, int fullScore, double percent, double rawPercent) {
            this.score = score;
            this.fullScore = fullScore;
            this.percent = percent;
            this.rawPercent = rawPercent;
        }
    }

    /** 标准分权重计算项：课程ID（custom 模式用）、得分率(%)、满分。 */
    public static class StdItem {
        public final long courseId;
        public final double percent;
        public final int fullScore;

        public StdItem(long courseId, double percent, int fullScore) {
            this.courseId = courseId;
            this.percent = percent;
            this.fullScore = fullScore;
        }
    }
}
