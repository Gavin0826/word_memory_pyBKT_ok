package com.wordmemory.backend.util;

import com.wordmemory.backend.repository.StudyRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BKT 在线推断服务
 *
 * 基于贝叶斯知识追踪（Bayesian Knowledge Tracing）模型，
 * 对某用户-单词对的历史答题序列进行概率推断。
 *
 * 参数来源：由 pyBKT 在 skill_builder_bkt_lite.csv 上训练得到的
 * 平均参数，按单词难度分为三组。
 *
 * BKT 四参数含义：
 *   prior   - 初始掌握概率 P(L0)
 *   learns  - 学习转移概率 P(T)，每次练习后从未掌握转为掌握的概率
 *   guesses - 猜测概率 P(G)，未掌握时答对的概率
 *   slips   - 失误概率 P(S)，已掌握时答错的概率
 */
@Service
public class BktInferenceService {

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    // ==================== BKT 参数（按难度分组）====================
    // 参数来源：pyBKT 训练报告中15个技能的有效参数均值，按难度调整

    // easy：基础词汇，初始掌握概率高
    private static final double EASY_PRIOR   = 0.80;
    private static final double EASY_LEARNS  = 0.25;
    private static final double EASY_GUESSES = 0.30;
    private static final double EASY_SLIPS   = 0.08;

    // medium：中级词汇（默认）
    private static final double MEDIUM_PRIOR   = 0.60;
    private static final double MEDIUM_LEARNS  = 0.20;
    private static final double MEDIUM_GUESSES = 0.25;
    private static final double MEDIUM_SLIPS   = 0.12;

    // hard：高级词汇，初始掌握概率低
    private static final double HARD_PRIOR   = 0.40;
    private static final double HARD_LEARNS  = 0.15;
    private static final double HARD_GUESSES = 0.20;
    private static final double HARD_SLIPS   = 0.15;

    /**
     * 推断用户对某单词的当前掌握概率
     *
     * @param userId    用户ID
     * @param wordId    单词ID
     * @param difficulty 单词难度（"easy"/"medium"/"hard"）
     * @return 掌握概率 [0.05, 0.98]
     */
    public double inferMastery(Long userId, Long wordId, String difficulty) {
        // 1. 获取 BKT 参数
        double[] params = getParams(difficulty);
        double prior   = params[0];
        double learns  = params[1];
        double guesses = params[2];
        double slips   = params[3];

        // 2. 从数据库读取该用户-单词的历史答题序列（按时间升序）
        List<Boolean> history = studyRecordRepository
                .findIsCorrectByUserIdAndWordIdOrderByStudyTimeAsc(userId, wordId);

        // 3. BKT 递推推断
        double pKnow = prior;
        for (Boolean correct : history) {
            pKnow = bktUpdate(pKnow, correct, guesses, slips, learns);
        }

        // 4. 限制范围，避免极端值
        return Math.max(0.05, Math.min(0.98, Math.round(pKnow * 10000.0) / 10000.0));
    }

    /**
     * BKT 单步更新
     *
     * 公式：
     *   P(L|correct) = P(L) * (1-slip) / [P(L)*(1-slip) + (1-P(L))*guess]
     *   P(L|wrong)   = P(L) * slip     / [P(L)*slip     + (1-P(L))*(1-guess)]
     *   P(L_new)     = P(L_posterior) + (1 - P(L_posterior)) * learns
     */
    private double bktUpdate(double pKnow, boolean correct,
                              double guesses, double slips, double learns) {
        double posterior;
        if (correct) {
            double evidence = pKnow * (1 - slips) + (1 - pKnow) * guesses;
            if (evidence < 1e-10) return pKnow;
            posterior = pKnow * (1 - slips) / evidence;
        } else {
            double evidence = pKnow * slips + (1 - pKnow) * (1 - guesses);
            if (evidence < 1e-10) return pKnow;
            posterior = pKnow * slips / evidence;
        }
        // 学习转移：答完这题后，未掌握的学生有 learns 的概率学会
        return posterior + (1 - posterior) * learns;
    }

    /**
     * 根据难度返回 BKT 参数数组 [prior, learns, guesses, slips]
     */
    private double[] getParams(String difficulty) {
        if (difficulty == null) return new double[]{MEDIUM_PRIOR, MEDIUM_LEARNS, MEDIUM_GUESSES, MEDIUM_SLIPS};
        switch (difficulty.toLowerCase()) {
            case "easy":   return new double[]{EASY_PRIOR,   EASY_LEARNS,   EASY_GUESSES,   EASY_SLIPS};
            case "hard":   return new double[]{HARD_PRIOR,   HARD_LEARNS,   HARD_GUESSES,   HARD_SLIPS};
            default:       return new double[]{MEDIUM_PRIOR, MEDIUM_LEARNS, MEDIUM_GUESSES, MEDIUM_SLIPS};
        }
    }
}
