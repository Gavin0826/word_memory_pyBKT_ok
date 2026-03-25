package com.wordmemory.backend.util;

/**
 * SM-2 调度器（SuperMemo 2）
 *
 * 当前项目约定：
 * - 正确 -> q = 5
 * - 错误 -> q = 2
 *
 * 单位说明：
 * - intervalDays 使用“天”为单位，nextReviewTime 由 Controller 转换为 LocalDateTime。
 */
public class Sm2Scheduler {

    public static class Result {
        public final double easeFactor;
        public final int repetition;
        public final int intervalDays;

        public Result(double easeFactor, int repetition, int intervalDays) {
            this.easeFactor = easeFactor;
            this.repetition = repetition;
            this.intervalDays = intervalDays;
        }
    }

    /**
     * @param prevEaseFactor 上一次的 EF（ease factor）
     * @param prevRepetition 上一次的 repetition（成功复习次数）
     * @param prevIntervalDays 上一次的 intervalDays
     * @param q 质量评分，通常 0~5；本项目由 correct/wrong 映射得到
     */
    public static Result next(double prevEaseFactor, int prevRepetition, int prevIntervalDays, int q) {
        // clamp q to [0,5]
        int quality = Math.max(0, Math.min(5, q));

        // EF 更新公式
        double ef = prevEaseFactor
                + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        ef = Math.max(ef, 1.3);

        int repetition;
        int intervalDays;

        if (quality < 3) {
            // 失败：repetition 归零，下一次最小间隔为 1 天
            repetition = 0;
            intervalDays = 1;
        } else {
            repetition = prevRepetition + 1;
            if (repetition == 1) {
                intervalDays = 1;
            } else if (repetition == 2) {
                intervalDays = 6;
            } else {
                int base = prevIntervalDays <= 0 ? 1 : prevIntervalDays;
                intervalDays = (int) Math.round(base * ef);
                intervalDays = Math.max(intervalDays, 1);
            }
        }

        return new Result(ef, repetition, intervalDays);
    }
}

