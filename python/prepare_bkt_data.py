import csv
from collections import Counter
from io import StringIO
from pathlib import Path


INPUT_FILE = Path(__file__).parent / "skill_builder_data.csv"
OUTPUT_FILE = Path(__file__).parent / "skill_builder_bkt_ready.csv"
REPORT_FILE = Path(__file__).parent / "skill_builder_bkt_report.txt"


def to_int(value, default=None):
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def read_csv_text_with_best_encoding(path: Path):
    """尝试多种编码读取整个文件，确保不会读到一半报错。"""
    encodings = ["utf-8", "utf-8-sig", "gbk", "cp1252", "latin1"]
    for enc in encodings:
        try:
            text = path.read_text(encoding=enc)
            return text, enc
        except UnicodeDecodeError:
            continue
    raise UnicodeDecodeError("decode", b"", 0, 1, f"无法识别文件编码: {path}")


def main():
    if not INPUT_FILE.exists():
        raise FileNotFoundError(f"Input file not found: {INPUT_FILE}")

    text, used_encoding = read_csv_text_with_best_encoding(INPUT_FILE)
    reader = csv.DictReader(StringIO(text))

    required = {"user_id", "skill_name", "correct", "order_id"}
    missing = required - set(reader.fieldnames or [])
    if missing:
        raise ValueError(f"Missing required columns: {missing}")

    rows = []
    total_rows = 0
    dropped_missing_skill = 0
    dropped_bad_correct = 0
    dropped_bad_order = 0

    for r in reader:
        total_rows += 1

        user_id = str(r.get("user_id", "")).strip()
        skill_name = str(r.get("skill_name", "")).strip()
        correct_raw = str(r.get("correct", "")).strip()
        order_raw = str(r.get("order_id", "")).strip()

        if not skill_name:
            dropped_missing_skill += 1
            continue

        correct = to_int(correct_raw)
        if correct not in (0, 1):
            dropped_bad_correct += 1
            continue

        order_id = to_int(order_raw)
        if order_id is None:
            dropped_bad_order += 1
            continue

        rows.append({
            "user_id": user_id,
            "skill_name": skill_name,
            "correct": correct,
            "order_id": order_id,
        })

    # 排序：先用户，再顺序
    rows.sort(key=lambda x: (x["user_id"], x["order_id"]))

    # 写出 pyBKT 训练友好格式
    with OUTPUT_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["user_id", "skill_name", "correct", "order_id"])
        writer.writeheader()
        writer.writerows(rows)

    # 统计报告
    users = {r["user_id"] for r in rows}
    skill_counts = Counter(r["skill_name"] for r in rows)
    correct_count = sum(r["correct"] for r in rows)
    accuracy = (correct_count / len(rows) * 100.0) if rows else 0.0

    sparse_thresholds = [5, 20, 50]
    sparse_counts = {t: sum(1 for _, c in skill_counts.items() if c < t) for t in sparse_thresholds}
    top10_skills = skill_counts.most_common(10)

    report_lines = [
        "=== skill_builder_data 预处理报告（pyBKT）===",
        f"输入文件: {INPUT_FILE}",
        f"输出文件: {OUTPUT_FILE}",
        f"读取编码: {used_encoding}",
        "",
        f"原始总行数: {total_rows}",
        f"保留行数: {len(rows)}",
        f"删除-缺失skill_name: {dropped_missing_skill}",
        f"删除-correct非0/1: {dropped_bad_correct}",
        f"删除-order_id非法: {dropped_bad_order}",
        "",
        f"用户数(user_id): {len(users)}",
        f"技能数(skill_name): {len(skill_counts)}",
        f"整体正确率: {accuracy:.2f}%",
        "",
        "样本稀疏技能统计:",
    ]

    for t in sparse_thresholds:
        report_lines.append(f"  样本数 < {t} 的skill数量: {sparse_counts[t]}")

    report_lines.append("")
    report_lines.append("Top10 技能样本数:")
    for name, cnt in top10_skills:
        report_lines.append(f"  {name}: {cnt}")

    REPORT_FILE.write_text("\n".join(report_lines), encoding="utf-8")

    print("预处理完成")
    print(f"输出: {OUTPUT_FILE}")
    print(f"报告: {REPORT_FILE}")


if __name__ == "__main__":
    main()
