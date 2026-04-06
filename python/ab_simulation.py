"""
ab_simulation.py

A/B 对照仿真实验：BKT-SM2 融合算法 vs 固定 q SM-2

实验设计：
  - 组A（Control）：固定 q（答对=5，答错=2）驱动 SM-2
  - 组B（Treatment）：BKT 动态推断概率 → 映射 q（2/3/4/5）→ 驱动 SM-2
  - 20个虚拟用户 × 30个真实单词 × 6轮练习
  - 仿真数据不写入数据库

输出：
  - 文本报告：bkt_outputs/ab_simulation_report.txt
  - 图表：bkt_outputs/ab_simulation_charts.png
"""

import random
import math
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Dict

try:
    import numpy as np
except ImportError:
    raise RuntimeError("请先安装：pip install numpy")

try:
    import matplotlib
    matplotlib.use('Agg')  # 非交互模式，保存为文件
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
except ImportError:
    raise RuntimeError("请先安装：pip install matplotlib")

try:
    import mysql.connector
except ImportError:
    raise RuntimeError("请先安装：pip install mysql-connector-python")

# ==================== 配置 ====================
DB_CONFIG = {
    "host": "localhost", "port": 3306,
    "user": "root", "password": "thm040826",
    "database": "word_memory_db", "charset": "utf8mb4"
}

NUM_USERS   = 20    # 虚拟用户数
NUM_WORDS   = 30    # 每用户练习单词数
NUM_ROUNDS  = 6     # 每个单词练习轮数
RANDOM_SEED = 42

OUT_DIR = Path(__file__).parent / "bkt_outputs"
OUT_DIR.mkdir(exist_ok=True)
REPORT_FILE = OUT_DIR / "ab_simulation_report.txt"
CHART_FILE  = OUT_DIR / "ab_simulation_charts.png"

# BKT 参数（来自 pyBKT 训练报告，按难度分组）
BKT_PARAMS = {
    "easy":   {"prior": 0.80, "learns": 0.25, "guesses": 0.30, "slips": 0.08},
    "medium": {"prior": 0.60, "learns": 0.20, "guesses": 0.25, "slips": 0.12},
    "hard":   {"prior": 0.40, "learns": 0.15, "guesses": 0.20, "slips": 0.15},
}

# 各难度答对概率（模拟真实用户）
CORRECT_PROB = {"easy": 0.85, "medium": 0.65, "hard": 0.45}


# ==================== 数据结构 ====================
@dataclass
class WordInfo:
    word_id: int
    word: str
    difficulty: str


@dataclass
class Sm2State:
    ef: float = 2.5
    repetition: int = 0
    interval_days: int = 0


@dataclass
class RoundRecord:
    user_id: int
    word_id: int
    difficulty: str
    round_no: int
    is_correct: bool
    q_score: int
    ef: float
    repetition: int
    interval_days: int
    bkt_prob: float
    group: str  # 'A' or 'B'


# ==================== SM-2 算法 ====================
def sm2_next(state: Sm2State, q: int) -> Sm2State:
    quality = max(0, min(5, q))
    ef = state.ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
    ef = max(ef, 1.3)
    if quality < 3:
        return Sm2State(ef=ef, repetition=0, interval_days=1)
    rep = state.repetition + 1
    if rep == 1:
        interval = 1
    elif rep == 2:
        interval = 6
    else:
        base = max(state.interval_days, 1)
        interval = max(1, round(base * ef))
    return Sm2State(ef=ef, repetition=rep, interval_days=interval)


# ==================== BKT 推断 ====================
def bkt_infer(history: List[bool], difficulty: str) -> float:
    p = BKT_PARAMS[difficulty]
    prior   = p["prior"]
    learns  = p["learns"]
    guesses = p["guesses"]
    slips   = p["slips"]
    pk = prior
    for correct in history:
        if correct:
            ev = pk * (1 - slips) + (1 - pk) * guesses
            posterior = pk * (1 - slips) / ev if ev > 1e-10 else pk
        else:
            ev = pk * slips + (1 - pk) * (1 - guesses)
            posterior = pk * slips / ev if ev > 1e-10 else pk
        pk = posterior + (1 - posterior) * learns
    return max(0.05, min(0.98, pk))


def map_prob_to_q(p: float, is_correct: bool) -> int:
    if p >= 0.85: q = 5
    elif p >= 0.70: q = 4
    elif p >= 0.50: q = 3
    else: q = 2
    if is_correct: return max(q, 3)
    return min(q, 2)


# ==================== 读取真实单词 ====================
def load_words(n: int) -> List[WordInfo]:
    """按难度尽量均衡抽样单词（easy/medium/hard）。

    优先使用数据库真实 difficulty 分层抽样；若某些难度缺失，
    则回退到“随机取词 + 均衡分配仿真难度标签”，保证A/B实验样本均衡。
    """
    conn = mysql.connector.connect(**DB_CONFIG)
    cur = conn.cursor()

    diffs = ['easy', 'medium', 'hard']
    base = n // 3
    remainder = n % 3
    target = {d: base for d in diffs}
    for i in range(remainder):
        target[diffs[i]] += 1

    # 统计数据库中三类难度可用数量
    cur.execute("SELECT difficulty, COUNT(*) FROM word GROUP BY difficulty")
    raw_counts = {k: int(v) for k, v in cur.fetchall() if k in diffs}
    counts = {d: raw_counts.get(d, 0) for d in diffs}

    words: List[WordInfo] = []

    # 情况1：三类难度都存在，按真实difficulty分层抽样
    if all(counts[d] > 0 for d in diffs):
        selected_ids = set()
        for d in diffs:
            cur.execute(
                "SELECT id, word, difficulty FROM word WHERE difficulty = %s ORDER BY RAND() LIMIT %s",
                (d, target[d])
            )
            rows = cur.fetchall()
            for r in rows:
                words.append(WordInfo(word_id=r[0], word=r[1], difficulty=d))
                selected_ids.add(r[0])

        # 若某类不足，再从全体补齐
        if len(words) < n:
            cur.execute(
                "SELECT id, word, difficulty FROM word WHERE difficulty IN ('easy','medium','hard') ORDER BY RAND()"
            )
            for r in cur.fetchall():
                if r[0] in selected_ids:
                    continue
                diff = r[2] if r[2] in diffs else 'medium'
                words.append(WordInfo(word_id=r[0], word=r[1], difficulty=diff))
                selected_ids.add(r[0])
                if len(words) >= n:
                    break

    # 情况2：难度分布严重不均（如只有easy），回退为“均衡仿真标签”
    else:
        cur.execute("SELECT id, word FROM word ORDER BY RAND() LIMIT %s", (n,))
        rows = cur.fetchall()

        # 均衡目标标签
        label_pool: List[str] = []
        for d in diffs:
            label_pool.extend([d] * target[d])
        random.shuffle(label_pool)

        for i, r in enumerate(rows):
            sim_diff = label_pool[i] if i < len(label_pool) else 'medium'
            words.append(WordInfo(word_id=r[0], word=r[1], difficulty=sim_diff))

    conn.close()

    random.shuffle(words)

    # 兜底补齐（极端情况下）
    while len(words) < n:
        words.append(WordInfo(word_id=9999 + len(words), word="word", difficulty="medium"))

    return words[:n]


# ==================== 仿真单组 ====================
def simulate_group(group: str, words: List[WordInfo], rng: random.Random) -> List[RoundRecord]:
    records = []
    for uid in range(NUM_USERS):
        for w in words:
            state = Sm2State()
            history: List[bool] = []
            for rnd in range(1, NUM_ROUNDS + 1):
                # 模拟答题（按难度设定答对概率）
                correct_prob = CORRECT_PROB[w.difficulty]
                # 随着练习轮次增加，答对概率略微提升
                adjusted_prob = min(0.97, correct_prob + rnd * 0.02)
                is_correct = rng.random() < adjusted_prob
                history.append(is_correct)

                # 决定 q 值
                if group == 'A':
                    q = 5 if is_correct else 2
                    bkt_prob = 0.5  # 组A不用BKT，记录固定值方便对比
                else:
                    bkt_prob = bkt_infer(history[:-1], w.difficulty)  # 用本轮前的历史推断
                    q = map_prob_to_q(bkt_prob, is_correct)

                # SM-2 更新
                new_state = sm2_next(state, q)
                records.append(RoundRecord(
                    user_id=uid,
                    word_id=w.word_id,
                    difficulty=w.difficulty,
                    round_no=rnd,
                    is_correct=is_correct,
                    q_score=q,
                    ef=new_state.ef,
                    repetition=new_state.repetition,
                    interval_days=new_state.interval_days,
                    bkt_prob=bkt_prob,
                    group=group
                ))
                state = new_state
    return records


# ==================== 统计分析 ====================
def analyze(records: List[RoundRecord]) -> dict:
    if not records:
        return {}
    n = len(records)
    correct = sum(1 for r in records if r.is_correct)
    intervals = [r.interval_days for r in records]
    efs = [r.ef for r in records]
    q_dist: Dict[int, int] = {}
    for r in records:
        q_dist[r.q_score] = q_dist.get(r.q_score, 0) + 1
    diff_dist: Dict[str, int] = {}
    for r in records:
        diff_dist[r.difficulty] = diff_dist.get(r.difficulty, 0) + 1
    # 按轮次统计平均BKT概率
    round_bkt: Dict[int, List[float]] = {}
    for r in records:
        round_bkt.setdefault(r.round_no, []).append(r.bkt_prob)
    avg_bkt_by_round = {k: round(sum(v)/len(v), 4) for k, v in sorted(round_bkt.items())}
    # 按轮次统计平均间隔
    round_interval: Dict[int, List[int]] = {}
    for r in records:
        round_interval.setdefault(r.round_no, []).append(r.interval_days)
    avg_interval_by_round = {k: round(sum(v)/len(v), 2) for k, v in sorted(round_interval.items())}
    return {
        "count": n,
        "accuracy_pct": round(correct / n * 100, 2),
        "avg_interval": round(sum(intervals) / n, 2),
        "std_interval": round(float(np.std(intervals)), 2),
        "avg_ef": round(sum(efs) / n, 4),
        "q_dist": dict(sorted(q_dist.items())),
        "diff_dist": diff_dist,
        "avg_bkt_by_round": avg_bkt_by_round,
        "avg_interval_by_round": avg_interval_by_round,
    }


# ==================== 生成报告 ====================
def format_report(sa: dict, sb: dict) -> str:
    lines = [
        "=" * 55,
        "  A/B 仿真对照实验报告",
        "  BKT-SM2 融合算法 vs 固定 q SM-2",
        "=" * 55,
        f"仿真规模：{NUM_USERS}用户 × {NUM_WORDS}单词 × {NUM_ROUNDS}轮",
        f"总记录数：{sa['count'] + sb['count']} 条（每组 {sa['count']} 条）",
        "",
        "─" * 55,
        "组A（Control）：固定 q，纯 SM-2",
        "─" * 55,
        f"  总记录数    : {sa['count']}",
        f"  正确率      : {sa['accuracy_pct']}%",
        f"  平均复习间隔: {sa['avg_interval']} 天（标准差 {sa['std_interval']}）",
        f"  平均 EF     : {sa['avg_ef']}",
        f"  q分布       : {sa['q_dist']}",
        f"  难度分布    : {sa['diff_dist']}",
        "",
        "─" * 55,
        "组B（Treatment）：BKT 动态映射 q，BKT-SM2 融合",
        "─" * 55,
        f"  总记录数    : {sb['count']}",
        f"  正确率      : {sb['accuracy_pct']}%",
        f"  平均复习间隔: {sb['avg_interval']} 天（标准差 {sb['std_interval']}）",
        f"  平均 EF     : {sb['avg_ef']}",
        f"  q分布       : {sb['q_dist']}",
        f"  难度分布    : {sb['diff_dist']}",
        "",
        "─" * 55,
        "差异对比（B - A）",
        "─" * 55,
    ]
    d_interval = sb['avg_interval'] - sa['avg_interval']
    d_std      = sb['std_interval'] - sa['std_interval']
    d_acc      = sb['accuracy_pct'] - sa['accuracy_pct']
    d_ef       = round(sb['avg_ef'] - sa['avg_ef'], 4)
    lines += [
        f"  复习间隔差  : {d_interval:+.2f} 天",
        f"  间隔标准差差: {d_std:+.2f}（正值表示B组个性化程度更高）",
        f"  正确率差    : {d_acc:+.2f}%",
        f"  EF差        : {d_ef:+.4f}",
        "",
        "  q_score 分布对比：",
        f"    组A: {sa['q_dist']}",
        f"    组B: {sb['q_dist']}",
        "",
        "  按轮次的平均复习间隔（天）：",
        "    轮次  组A间隔  组B间隔",
    ]
    for rnd in range(1, NUM_ROUNDS + 1):
        a_i = sa['avg_interval_by_round'].get(rnd, 0)
        b_i = sb['avg_interval_by_round'].get(rnd, 0)
        lines.append(f"    第{rnd}轮  {a_i:>6.2f}   {b_i:>6.2f}")
    lines += [
        "",
        "  按轮次的平均 BKT 掌握概率（组B）：",
        "    轮次  BKT概率",
    ]
    for rnd, p in sb['avg_bkt_by_round'].items():
        lines.append(f"    第{rnd}轮  {p:.4f}")
    lines += [
        "",
        "=" * 55,
        "实验结论：",
    ]
    if d_std > 0:
        lines.append("  ✓ 组B复习间隔标准差更大，说明BKT-SM2算法能根据")
        lines.append("    掌握程度差异给出更个性化的复习间隔。")
    else:
        lines.append("  - 两组间隔标准差接近，建议增大样本量重跑。")
    if len(sb['q_dist']) > len(sa['q_dist']):
        lines.append("  ✓ 组B的q值分布更细粒度，体现了BKT概率的多级映射优势。")
    lines.append("  ✓ 实验数据已保存，可直接引用至论文实验章节。")
    lines.append("=" * 55)
    return "\n".join(lines)


# ==================== 生成图表 ====================
def plot_charts(sa: dict, sb: dict):
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle('A/B Simulation: BKT-SM2 vs Fixed-q SM2', fontsize=14, fontweight='bold')

    rounds = list(range(1, NUM_ROUNDS + 1))
    a_intervals = [sa['avg_interval_by_round'].get(r, 0) for r in rounds]
    b_intervals = [sb['avg_interval_by_round'].get(r, 0) for r in rounds]
    b_bkt = [sb['avg_bkt_by_round'].get(r, 0) for r in rounds]

    # 图1: 平均复习间隔对比（折线图）
    ax1 = axes[0, 0]
    ax1.plot(rounds, a_intervals, 'o-', color='#e74c3c', linewidth=2, label='Group A (Fixed-q)')
    ax1.plot(rounds, b_intervals, 's-', color='#2ecc71', linewidth=2, label='Group B (BKT-SM2)')
    ax1.set_title('Avg Review Interval by Round (days)')
    ax1.set_xlabel('Round')
    ax1.set_ylabel('Interval (days)')
    ax1.legend()
    ax1.grid(True, alpha=0.3)
    ax1.set_xticks(rounds)

    # 图2: BKT掌握概率增长曲线（组B）
    ax2 = axes[0, 1]
    ax2.plot(rounds, b_bkt, 'D-', color='#3498db', linewidth=2, label='BKT Mastery Prob (Group B)')
    ax2.axhline(y=0.70, color='orange', linestyle='--', alpha=0.7, label='p=0.70 threshold')
    ax2.axhline(y=0.85, color='red', linestyle='--', alpha=0.7, label='p=0.85 threshold')
    ax2.set_title('BKT Mastery Probability Growth (Group B)')
    ax2.set_xlabel('Round')
    ax2.set_ylabel('P(mastery)')
    ax2.set_ylim(0, 1)
    ax2.legend()
    ax2.grid(True, alpha=0.3)
    ax2.set_xticks(rounds)

    # 图3: q_score分布对比（柱状图）
    ax3 = axes[1, 0]
    all_q = sorted(set(list(sa['q_dist'].keys()) + list(sb['q_dist'].keys())))
    x = np.arange(len(all_q))
    w = 0.35
    a_counts = [sa['q_dist'].get(q, 0) for q in all_q]
    b_counts = [sb['q_dist'].get(q, 0) for q in all_q]
    ax3.bar(x - w/2, a_counts, w, label='Group A', color='#e74c3c', alpha=0.8)
    ax3.bar(x + w/2, b_counts, w, label='Group B', color='#2ecc71', alpha=0.8)
    ax3.set_title('q-score Distribution Comparison')
    ax3.set_xlabel('q score')
    ax3.set_ylabel('Count')
    ax3.set_xticks(x)
    ax3.set_xticklabels([str(q) for q in all_q])
    ax3.legend()
    ax3.grid(True, alpha=0.3, axis='y')

    # 图4: 平均复习间隔 & 标准差对比（柱状图）
    ax4 = axes[1, 1]
    metrics_labels = ['Avg Interval (days)', 'Std Interval', 'Avg EF']
    a_vals = [sa['avg_interval'], sa['std_interval'], sa['avg_ef']]
    b_vals = [sb['avg_interval'], sb['std_interval'], sb['avg_ef']]
    x4 = np.arange(len(metrics_labels))
    ax4.bar(x4 - w/2, a_vals, w, label='Group A', color='#e74c3c', alpha=0.8)
    ax4.bar(x4 + w/2, b_vals, w, label='Group B', color='#2ecc71', alpha=0.8)
    ax4.set_title('Key Metrics Comparison')
    ax4.set_xticks(x4)
    ax4.set_xticklabels(metrics_labels, fontsize=9)
    ax4.legend()
    ax4.grid(True, alpha=0.3, axis='y')

    plt.tight_layout()
    plt.savefig(CHART_FILE, dpi=150, bbox_inches='tight')
    plt.close()
    print(f'图表已保存: {CHART_FILE}')


# ==================== 主程序 ====================
def main():
    random.seed(RANDOM_SEED)
    rng = random.Random(RANDOM_SEED)

    print('正在从数据库加载真实单词...')
    words = load_words(NUM_WORDS)
    diff_counts = {}
    for w in words:
        diff_counts[w.difficulty] = diff_counts.get(w.difficulty, 0) + 1
    print(f'加载单词: {len(words)} 个，难度分布: {diff_counts}')

    print(f'开始仿真（{NUM_USERS}用户 × {NUM_WORDS}单词 × {NUM_ROUNDS}轮）...')
    print('运行组A（固定q）...')
    records_a = simulate_group('A', words, rng)
    print(f'  组A完成，共 {len(records_a)} 条记录')

    rng2 = random.Random(RANDOM_SEED)  # 相同随机种子确保答题序列一致
    print('运行组B（BKT-SM2）...')
    records_b = simulate_group('B', words, rng2)
    print(f'  组B完成，共 {len(records_b)} 条记录')

    print('统计分析中...')
    sa = analyze(records_a)
    sb = analyze(records_b)

    report = format_report(sa, sb)
    # 修复 Windows GBK 终端编码问题
    safe_report = report.replace('✓', '[OK]')
    print('\n' + safe_report)
    REPORT_FILE.write_text(report, encoding='utf-8')
    print(f'\n报告已保存: {REPORT_FILE}')

    print('生成图表...')
    plot_charts(sa, sb)
    print('全部完成！')


if __name__ == '__main__':
    main()
    