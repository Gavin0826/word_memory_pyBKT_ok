import csv
from collections import Counter, defaultdict
from pathlib import Path


INPUT_FILE = Path(__file__).parent / "skill_builder_bkt_ready.csv"
OUTPUT_FILE = Path(__file__).parent / "skill_builder_bkt_lite.csv"
REPORT_FILE = Path(__file__).parent / "skill_builder_bkt_lite_report.txt"

# 你可以按电脑性能调整这几个参数
TOP_SKILLS = 15                 # 只保留样本最多的前 N 个技能
MAX_ROWS_PER_SKILL = 1500       # 每个技能最多保留多少条
MAX_ROWS_PER_USER_SKILL = 20    # 每个用户-技能轨迹最多保留多少条（保留前几次作答）
MAX_TOTAL_ROWS = 20000          # 最终总行数上限


def to_int(x, default=0):
    try:
        return int(x)
    except Exception:
        return default


def main():
    if not INPUT_FILE.exists():
        raise FileNotFoundError(f"Input file not found: {INPUT_FILE}")

    rows = []
    with INPUT_FILE.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for r in reader:
            rows.append({
                "user_id": r["user_id"],
                "skill_name": r["skill_name"],
                "correct": to_int(r["correct"], 0),
                "order_id": to_int(r["order_id"], 0),
            })

    # 原始统计
    total_before = len(rows)
    users_before = len({r["user_id"] for r in rows})
    skills_before = Counter(r["skill_name"] for r in rows)

    # 1) 仅保留 top skills
    top_skills = set([s for s, _ in skills_before.most_common(TOP_SKILLS)])
    rows = [r for r in rows if r["skill_name"] in top_skills]

    # 2) 每个用户-技能只保留前 MAX_ROWS_PER_USER_SKILL 条（按 order_id）
    rows.sort(key=lambda x: (x["user_id"], x["skill_name"], x["order_id"]))
    us_counter = defaultdict(int)
    tmp = []
    for r in rows:
        key = (r["user_id"], r["skill_name"])
        if us_counter[key] < MAX_ROWS_PER_USER_SKILL:
            tmp.append(r)
            us_counter[key] += 1
    rows = tmp

    # 3) 每个技能最多 MAX_ROWS_PER_SKILL
    rows.sort(key=lambda x: (x["skill_name"], x["order_id"]))
    skill_counter = defaultdict(int)
    tmp = []
    for r in rows:
        s = r["skill_name"]
        if skill_counter[s] < MAX_ROWS_PER_SKILL:
            tmp.append(r)
            skill_counter[s] += 1
    rows = tmp

    # 4) 总行数上限
    rows.sort(key=lambda x: (x["user_id"], x["order_id"]))
    if len(rows) > MAX_TOTAL_ROWS:
        rows = rows[:MAX_TOTAL_ROWS]

    # 输出
    with OUTPUT_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["user_id", "skill_name", "correct", "order_id"])
        writer.writeheader()
        writer.writerows(rows)

    # 报告
    total_after = len(rows)
    users_after = len({r["user_id"] for r in rows})
    skills_after = Counter(r["skill_name"] for r in rows)
    acc = (sum(r["correct"] for r in rows) / total_after * 100.0) if total_after else 0.0

    lines = [
        "=== pyBKT 轻量数据集报告 ===",
        f"输入文件: {INPUT_FILE}",
        f"输出文件: {OUTPUT_FILE}",
        "",
        f"原始行数: {total_before}",
        f"精简后行数: {total_after}",
        f"压缩比例: {(1 - total_after / total_before) * 100:.2f}%" if total_before else "压缩比例: N/A",
        "",
        f"原始用户数: {users_before}",
        f"精简后用户数: {users_after}",
        f"原始技能数: {len(skills_before)}",
        f"精简后技能数: {len(skills_after)}",
        f"精简后正确率: {acc:.2f}%",
        "",
        "参数:",
        f"- TOP_SKILLS = {TOP_SKILLS}",
        f"- MAX_ROWS_PER_SKILL = {MAX_ROWS_PER_SKILL}",
        f"- MAX_ROWS_PER_USER_SKILL = {MAX_ROWS_PER_USER_SKILL}",
        f"- MAX_TOTAL_ROWS = {MAX_TOTAL_ROWS}",
        "",
        "Top 10 skills after sampling:",
    ]

    for s, c in skills_after.most_common(10):
        lines.append(f"- {s}: {c}")

    REPORT_FILE.write_text("\n".join(lines), encoding="utf-8")

    print("轻量数据集生成完成")
    print(OUTPUT_FILE)
    print(REPORT_FILE)


if __name__ == "__main__":
    main()
