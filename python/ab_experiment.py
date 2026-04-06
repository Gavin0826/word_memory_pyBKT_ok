"""ab_experiment.py

从 word_memory_db 数据库读取 study_record，
对比 A/B 两组的 SM-2 调度指标：
  - 组A：q_score 为 NULL（旧逻辑，固定 q）
  - 组B：q_score 不为 NULL（新逻辑，pyBKT 映射 q）

输出指标：
  - 平均 interval_days（复习间隔）
  - 正确率
  - q_score 分布
  - 各组记录数量
  - 报告写入 bkt_outputs/ab_experiment_report.txt
"""

import json
from pathlib import Path

try:
    import mysql.connector
except ImportError:
    raise RuntimeError(
        "缺少 mysql-connector-python，请先安装：pip install mysql-connector-python"
    )

try:
    import pandas as pd
except ImportError:
    raise RuntimeError("缺少 pandas，请先安装：pip install pandas")

# ==================== 数据库连接配置 ====================
DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "thm040826",
    "database": "word_memory_db",
    "charset": "utf8mb4"
}

OUT_DIR = Path(__file__).parent / "bkt_outputs"
OUT_DIR.mkdir(exist_ok=True)
REPORT_FILE = OUT_DIR / "ab_experiment_report.txt"
DATA_FILE = OUT_DIR / "ab_experiment_data.csv"


def fetch_study_records() -> pd.DataFrame:
    """从数据库读取 study_record 表"""
    conn = mysql.connector.connect(**DB_CONFIG)
    query = """
        SELECT
            sr.id,
            sr.user_id,
            sr.word_id,
            sr.is_correct,
            sr.study_type,
            sr.ease_factor,
            sr.repetition,
            sr.interval_days,
            sr.q_score,
            sr.consecutive_correct,
            sr.study_time,
            sr.next_review_time,
            w.difficulty,
            w.category
        FROM study_record sr
        LEFT JOIN word w ON sr.word_id = w.id
        WHERE sr.ease_factor IS NOT NULL
    """
    df = pd.read_sql(query, conn)
    conn.close()
    return df


def analyze_groups(df: pd.DataFrame):
    """将数据分为 A/B 两组并计算指标"""
    # 组A：q_score 为 NULL（旧逻辑）
    group_a = df[df["q_score"].isna()].copy()
    # 组B：q_score 不为 NULL（新 pyBKT 逻辑）
    group_b = df[df["q_score"].notna()].copy()

    def metrics(g: pd.DataFrame, name: str) -> dict:
        if g.empty:
            return {"group": name, "count": 0, "note": "无数据"}

        correct = g["is_correct"].apply(lambda x: bool(x) if not pd.isna(x) else False)
        accuracy = correct.mean() * 100

        avg_interval = g["interval_days"].mean()
        avg_ef = g["ease_factor"].mean()
        avg_repetition = g["repetition"].mean()

        # q_score 分布（组B专用）
        q_dist = {}
        if "q_score" in g.columns and g["q_score"].notna().any():
            q_counts = g["q_score"].value_counts().sort_index()
            q_dist = {int(k): int(v) for k, v in q_counts.items()}

        # 难度分布
        diff_dist = {}
        if "difficulty" in g.columns:
            diff_counts = g["difficulty"].value_counts()
            diff_dist = {k: int(v) for k, v in diff_counts.items()}

        return {
            "group": name,
            "count": len(g),
            "accuracy_pct": round(accuracy, 2),
            "avg_interval_days": round(avg_interval, 2),
            "avg_ease_factor": round(avg_ef, 4),
            "avg_repetition": round(avg_repetition, 2),
            "q_score_distribution": q_dist,
            "difficulty_distribution": diff_dist
        }

    return metrics(group_a, "A（固定q，旧逻辑）"), metrics(group_b, "B（pyBKT映射q，新逻辑）")


def format_report(ma: dict, mb: dict, total: int) -> str:
    lines = [
        "=== A/B 对照实验报告 ===",
        f"总记录数（含SM-2状态）: {total}",
        "",
        "--- 组A：固定 q（旧逻辑，q_score=NULL）---",
        f"  记录数: {ma.get('count', 0)}",
    ]
    if ma.get("count", 0) > 0:
        lines += [
            f"  正确率: {ma['accuracy_pct']}%",
            f"  平均复习间隔: {ma['avg_interval_days']} 天",
            f"  平均 EF: {ma['avg_ease_factor']}",
            f"  平均 repetition: {ma['avg_repetition']}",
        ]
    else:
        lines.append("  （暂无数据）")

    lines += [
        "",
        "--- 组B：pyBKT 映射 q（新逻辑，q_score 有值）---",
        f"  记录数: {mb.get('count', 0)}",
    ]
    if mb.get("count", 0) > 0:
        lines += [
            f"  正确率: {mb['accuracy_pct']}%",
            f"  平均复习间隔: {mb['avg_interval_days']} 天",
            f"  平均 EF: {mb['avg_ease_factor']}",
            f"  平均 repetition: {mb['avg_repetition']}",
            f"  q_score 分布: {mb['q_score_distribution']}",
            f"  难度分布: {mb['difficulty_distribution']}",
        ]
    else:
        lines.append("  （暂无数据）")

    # 差异对比
    if ma.get("count", 0) > 0 and mb.get("count", 0) > 0:
        diff_interval = mb["avg_interval_days"] - ma["avg_interval_days"]
        diff_acc = mb["accuracy_pct"] - ma["accuracy_pct"]
        lines += [
            "",
            "--- 差异对比（B - A）---",
            f"  复习间隔差: {diff_interval:+.2f} 天  {'（B间隔更长，调度更积极）' if diff_interval > 0 else '（B间隔更短，调度更保守）' if diff_interval < 0 else '（相同）'}",
            f"  正确率差:   {diff_acc:+.2f}%  {'（B正确率更高）' if diff_acc > 0 else '（A正确率更高）' if diff_acc < 0 else '（相同）'}",
        ]

    lines += [
        "",
        "说明：",
        "  组A = 使用旧版固定 q（正确=5，错误=2）的历史记录",
        "  组B = 使用 pyBKT 概率映射 q 的新记录（需重启后端答题才能产生）",
        "  当前阶段组B数据量较少属正常，随使用积累后对比将更有意义。"
    ]
    return "\n".join(lines)


def main():
    print("正在连接数据库并读取学习记录...")
    df = fetch_study_records()
    print(f"共读取 {len(df)} 条记录")

    if df.empty:
        print("暂无数据，请先使用系统答题后再运行。")
        return

    df.to_csv(DATA_FILE, index=False, encoding="utf-8-sig")
    print(f"原始数据已保存: {DATA_FILE}")

    # 筛选有 SM-2 状态的记录
    df_sm2 = df[df["ease_factor"].notna()].copy()
    print(f"含 SM-2 状态的记录: {len(df_sm2)}")

    ma, mb = analyze_groups(df_sm2)

    report = format_report(ma, mb, len(df_sm2))
    print("\n" + report)

    REPORT_FILE.write_text(report, encoding="utf-8")
    print(f"\n报告已保存: {REPORT_FILE}")


if __name__ == "__main__":
    main()
