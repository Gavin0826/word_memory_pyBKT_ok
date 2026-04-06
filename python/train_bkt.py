import json
from pathlib import Path

import pandas as pd


DATA_FILE = Path(__file__).parent / "skill_builder_bkt_lite.csv"
OUT_DIR = Path(__file__).parent / "bkt_outputs"
OUT_DIR.mkdir(exist_ok=True)

TRAIN_FILE = OUT_DIR / "train_split.csv"
TEST_FILE = OUT_DIR / "test_split.csv"
PRED_FILE = OUT_DIR / "test_predictions.csv"
REPORT_FILE = OUT_DIR / "bkt_train_report.txt"
PARAMS_FILE = OUT_DIR / "bkt_params.json"
PARAMS_CSV_FILE = OUT_DIR / "bkt_params_table.csv"  # 新增：可读参数表


def split_by_order(df: pd.DataFrame, train_ratio: float = 0.8):
    """
    按 user_id + skill_name 分组后，基于 order_id 做序列切分：
    每个序列前 train_ratio 用于训练，后续用于测试。
    这样能保证"用过去预测未来"，且避免大量冷启动样本落入测试集。
    """
    train_parts = []
    test_parts = []

    grouped = df.sort_values(["user_id", "skill_name", "order_id"]).groupby(
        ["user_id", "skill_name"], sort=False
    )

    for _, g in grouped:
        n = len(g)
        if n <= 2:
            train_parts.append(g)
            continue

        cut = max(1, int(n * train_ratio))
        if cut >= n:
            cut = n - 1

        train_parts.append(g.iloc[:cut])
        test_parts.append(g.iloc[cut:])

    train_df = pd.concat(train_parts, ignore_index=True) if train_parts else df.copy()
    test_df = pd.concat(test_parts, ignore_index=True) if test_parts else df.iloc[0:0].copy()

    if test_df.empty:
        cutoff = df["order_id"].quantile(train_ratio)
        train_df = df[df["order_id"] <= cutoff].copy()
        test_df = df[df["order_id"] > cutoff].copy()

    return train_df, test_df


def get_prediction_column(pred_df: pd.DataFrame):
    candidates = [
        "correct_predictions",
        "correct_prediction",
        "prediction",
        "predicted_correct",
        "y_pred",
    ]
    for c in candidates:
        if c in pred_df.columns:
            return c
    raise ValueError(f"无法识别预测列，当前列名: {list(pred_df.columns)}")


def prepare_eval_arrays(pred_df: pd.DataFrame, pred_col: str):
    eval_df = pred_df[["correct", pred_col]].copy()
    eval_df["correct"] = pd.to_numeric(eval_df["correct"], errors="coerce")
    eval_df[pred_col] = pd.to_numeric(eval_df[pred_col], errors="coerce")
    eval_df = eval_df.dropna()
    eval_df["correct"] = eval_df["correct"].astype(int)
    eval_df[pred_col] = eval_df[pred_col].clip(0, 1)

    if eval_df.empty:
        raise ValueError("预测结果为空（可能全部是 NaN），无法评估。")

    y_true = eval_df["correct"]
    y_prob = eval_df[pred_col]
    return y_true, y_prob, len(eval_df)


def extract_params_to_table(model, skills: list) -> pd.DataFrame:
    """
    强制从 pyBKT 模型中提取四个核心参数（prior/learns/guesses/slips），
    兼容多个 pyBKT 版本的参数存储格式，返回 DataFrame。
    """
    rows = []

    # === 方式1：新版 model.params() 返回 DataFrame ===
    try:
        params_df = model.params()
        if isinstance(params_df, pd.DataFrame):
            for skill in skills:
                row = {"skill_name": skill, "prior": None, "learns": None, "guesses": None, "slips": None}
                try:
                    skill_rows = params_df[params_df.index.get_level_values(0) == skill]
                    if not skill_rows.empty:
                        for col in skill_rows.columns:
                            col_lower = col.lower()
                            if "prior" in col_lower and row["prior"] is None:
                                row["prior"] = float(skill_rows[col].iloc[0])
                            elif "learn" in col_lower and row["learns"] is None:
                                row["learns"] = float(skill_rows[col].iloc[0])
                            elif "guess" in col_lower and row["guesses"] is None:
                                row["guesses"] = float(skill_rows[col].iloc[0])
                            elif "slip" in col_lower and row["slips"] is None:
                                row["slips"] = float(skill_rows[col].iloc[0])
                except Exception:
                    pass
                rows.append(row)
            if any(r["prior"] is not None for r in rows):
                return pd.DataFrame(rows)
    except Exception:
        pass

    # === 方式2：旧版 model.coef_ 字典结构 ===
    try:
        coef = model.coef_
        if isinstance(coef, dict):
            for skill in skills:
                row = {"skill_name": skill, "prior": None, "learns": None, "guesses": None, "slips": None}
                skill_data = coef.get(skill, {})
                if isinstance(skill_data, dict):
                    row["prior"] = _safe_float(skill_data.get("prior"))
                    row["learns"] = _safe_float(skill_data.get("learns") or skill_data.get("learn"))
                    row["guesses"] = _safe_float(skill_data.get("guesses") or skill_data.get("guess"))
                    row["slips"] = _safe_float(skill_data.get("slips") or skill_data.get("slip"))
                rows.append(row)
            if any(r["prior"] is not None for r in rows):
                return pd.DataFrame(rows)
    except Exception:
        pass

    # === 方式3：遍历模型属性暴力搜索 ===
    try:
        for attr_name in dir(model):
            if attr_name.startswith("_"):
                continue
            try:
                attr = getattr(model, attr_name)
                if isinstance(attr, dict) and len(attr) > 0:
                    first_val = next(iter(attr.values()))
                    if isinstance(first_val, dict) and any(
                        k in first_val for k in ["prior", "learn", "guess", "slip"]
                    ):
                        for skill in skills:
                            row = {"skill_name": skill, "prior": None, "learns": None, "guesses": None, "slips": None}
                            skill_data = attr.get(skill, {})
                            row["prior"] = _safe_float(skill_data.get("prior"))
                            row["learns"] = _safe_float(skill_data.get("learns") or skill_data.get("learn"))
                            row["guesses"] = _safe_float(skill_data.get("guesses") or skill_data.get("guess"))
                            row["slips"] = _safe_float(skill_data.get("slips") or skill_data.get("slip"))
                            rows.append(row)
                        if any(r["prior"] is not None for r in rows):
                            return pd.DataFrame(rows)
            except Exception:
                continue
    except Exception:
        pass

    # === 方式4：全部失败，输出带 None 的占位行（至少文件能写出来）===
    for skill in skills:
        rows.append({"skill_name": skill, "prior": None, "learns": None, "guesses": None, "slips": None})
    return pd.DataFrame(rows)


def _safe_float(val):
    try:
        if val is None:
            return None
        return float(val)
    except Exception:
        return None


def main():
    try:
        from pyBKT.models import Model
    except Exception as e:
        raise RuntimeError(
            "pyBKT 导入失败。常见原因：Python 版本过新（如 3.14）或 sklearn 与 pyBKT 版本不兼容。"
            "\n建议使用 Python 3.10/3.11 新环境后重试。"
            f"\n原始错误: {e}"
        ) from e

    if not DATA_FILE.exists():
        raise FileNotFoundError(f"数据文件不存在: {DATA_FILE}")

    df = pd.read_csv(DATA_FILE)
    required_cols = {"user_id", "skill_name", "correct", "order_id"}
    missing = required_cols - set(df.columns)
    if missing:
        raise ValueError(f"数据缺少必要列: {missing}")

    # 基础清洗
    df = df[["user_id", "skill_name", "correct", "order_id"]].dropna().copy()
    df["correct"] = df["correct"].astype(int)
    df = df[df["correct"].isin([0, 1])]
    df["order_id"] = df["order_id"].astype(int)
    df["user_id"] = df["user_id"].astype(str)
    df["skill_name"] = df["skill_name"].astype(str)

    train_df, test_df = split_by_order(df, train_ratio=0.8)

    train_df.to_csv(TRAIN_FILE, index=False)
    test_df.to_csv(TEST_FILE, index=False)

    # 训练 pyBKT
    model = Model(seed=42, num_fits=1)
    skills = sorted(train_df["skill_name"].unique().tolist())
    model.fit(data=train_df, skills=skills, forgets=True, parallel=False)

    # 预测
    pred_df = model.predict(data=test_df)
    pred_col = get_prediction_column(pred_df)

    # 评估
    y_true, y_prob, eval_n = prepare_eval_arrays(pred_df, pred_col)
    y_pred = (y_prob >= 0.5).astype(int)
    accuracy = (y_true == y_pred).mean()
    baseline_acc = (y_true == 1).mean()

    auc_text = "N/A (未安装 sklearn)"
    auc_val = None
    try:
        from sklearn.metrics import roc_auc_score
        auc_val = roc_auc_score(y_true, y_prob)
        auc_text = f"{auc_val:.4f}"
    except Exception:
        pass

    pred_df.to_csv(PRED_FILE, index=False)

    # ==================== 强制提取并保存模型参数 ====================
    params_table = extract_params_to_table(model, skills)
    params_table.to_csv(PARAMS_CSV_FILE, index=False)
    print(f"参数表已保存: {PARAMS_CSV_FILE}")
    print(params_table.to_string(index=False))

    # 同时尝试保存 JSON（保留旧逻辑兼容）
    params_saved = False
    if hasattr(model, "params"):
        try:
            raw = model.params()
            if isinstance(raw, pd.DataFrame):
                params_obj = raw.to_dict()
            else:
                params_obj = raw
            with PARAMS_FILE.open("w", encoding="utf-8") as f:
                json.dump(params_obj, f, ensure_ascii=False, indent=2, default=str)
            params_saved = True
        except Exception:
            params_saved = False

    # 构建参数摘要行（用于报告）
    param_summary_lines = ["\n=== 四参数摘要（每个 Skill）==="]
    for _, row in params_table.iterrows():
        prior_str = f"{row['prior']:.4f}" if row['prior'] is not None else "N/A"
        learn_str = f"{row['learns']:.4f}" if row['learns'] is not None else "N/A"
        guess_str = f"{row['guesses']:.4f}" if row['guesses'] is not None else "N/A"
        slip_str = f"{row['slips']:.4f}" if row['slips'] is not None else "N/A"
        param_summary_lines.append(
            f"  {row['skill_name']}: prior={prior_str}, learns={learn_str}, guesses={guess_str}, slips={slip_str}"
        )

    lines = [
        "=== pyBKT 训练报告 ===",
        f"输入数据: {DATA_FILE}",
        f"训练集: {TRAIN_FILE}",
        f"测试集: {TEST_FILE}",
        f"预测结果: {PRED_FILE}",
        f"参数JSON: {PARAMS_FILE if params_saved else '未保存（当前pyBKT版本不支持或写入失败）'}",
        f"参数CSV: {PARAMS_CSV_FILE}",
        "",
        f"样本总数: {len(df)}",
        f"训练集样本: {len(train_df)}",
        f"测试集样本: {len(test_df)}",
        f"参与评估样本: {eval_n}",
        f"技能数: {df['skill_name'].nunique()}",
        f"用户数: {df['user_id'].nunique()}",
        "",
        f"Accuracy: {accuracy:.4f}",
        f"Baseline(全预测正确=1) Accuracy: {baseline_acc:.4f}",
        f"AUC: {auc_text}",
        f"预测列: {pred_col}",
    ] + param_summary_lines

    REPORT_FILE.write_text("\n".join(lines), encoding="utf-8")

    print("\nBKT 训练完成")
    print(f"报告: {REPORT_FILE}")
    print(f"Accuracy: {accuracy:.4f}  AUC: {auc_text}")


if __name__ == "__main__":
    main()
