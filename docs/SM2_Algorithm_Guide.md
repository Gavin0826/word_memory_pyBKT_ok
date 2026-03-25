# SM-2 算法参数说明文档

> 文件路径：`backend/src/main/java/com/wordmemory/backend/util/Sm2Scheduler.java`  
> 生成时间：2026-03-24

---

## 一、算法概述

本项目使用 **SM-2（SuperMemo 2）** 算法实现艾宾浩斯记忆曲线的自动调度。  
核心思想：根据每次答题质量动态调整下次复习的间隔时间，答对越多间隔越长，答错则重置间隔。

---

## 二、输入参数

调用 `Sm2Scheduler.next(prevEaseFactor, prevRepetition, prevIntervalDays, q)` 时，  
Controller 通过以下方式获取参数：

```java
StudyRecord latest = studyRecordRepository
    .findTopByUserIdAndWordIdOrderByStudyTimeDesc(userId, wordId);
```

| 参数 | 字段来源（数据库） | 含义 | 首次默认值 |
|---|---|---|---|
| `prevEaseFactor` | `latest.getEaseFactor()` | 上一次的易记性因子 EF | `2.5` |
| `prevRepetition` | `latest.getRepetition()` | 上一次成功复习的累计次数 | `0` |
| `prevIntervalDays` | `latest.getIntervalDays()` | 上一次计算出的间隔天数 | `0` |
| `q` | 由答题结果映射 | 本次答题质量评分（0~5） | 正确=5，错误=2 |

---

## 三、核心参数详解

### 3.1 `easeFactor`（EF，易记性因子）

**含义：** 该单词对用户来说有多容易记住。EF 越高，下次间隔越长。

**计算公式：**
```
new_EF = prev_EF + (0.1 - (5 - q) × (0.08 + (5 - q) × 0.02))
new_EF = max(new_EF, 1.3)   // 下限 1.3，防止间隔过短
```

**变化规律：**
| 答题结果 | q值 | EF 变化量 | 说明 |
|---|---|---|---|
| 答对 | 5 | +0.10 | 越来越容易，间隔拉长 |
| 答错 | 2 | −0.28 | 变难，间隔缩短 |

**范围：** 最低 `1.3`，初始 `2.5`，实际约在 `1.3 ~ 3.0` 之间波动。

---

### 3.2 `repetition`（重复次数）

**含义：** 该单词连续成功复习的累计次数（不是总答题次数）。

**变化规律：**
- 答对（q ≥ 3）：`repetition + 1`
- 答错（q < 3）：**归零（重置为 0）**，重新开始

**作用：** 决定走哪个间隔分支（见下方 intervalDays）。

---

### 3.3 `intervalDays`（复习间隔天数）

**含义：** 本次计算出的"距下次复习的天数"，最终转换为 `nextReviewTime`。

**三档计算规则：**

| repetition 值 | 计算方式 | 示例结果 |
|---|---|---|
| 1（第1次答对） | 固定 **1 天** | 明天复习 |
| 2（第2次连续答对） | 固定 **6 天** | 6天后复习 |
| ≥ 3（第3次及以后） | `round(prevIntervalDays × EF)` | 动态指数增长 |

**答错时：** 固定重置为 **1 天**，repetition 同时归零。

---

### 3.4 `q`（质量评分）

**含义：** 本次答题的表现质量，SM-2 原版为 0~5 分，本项目简化为二值映射：

| 答题结果 | q 值 | SM-2 语义 |
|---|---|---|
| 答对（isCorrect=true） | **5** | 完全记住，轻松回忆 |
| 答错（isCorrect=false） | **2** | 几乎忘记，需要重学 |

**阈值：** `q < 3` 视为失败，触发 repetition 归零和间隔重置。

---

## 四、输出参数写回数据库

`Sm2Scheduler.next()` 返回 `Result` 对象后，Controller 创建**新的 StudyRecord 行**写入：

```java
record.setEaseFactor(sm2.easeFactor);       // 新 EF
record.setRepetition(sm2.repetition);       // 新 repetition
record.setIntervalDays(sm2.intervalDays);   // 新间隔天数
record.setNextReviewTime(
    LocalDateTime.now().plusDays(sm2.intervalDays)); // 下次复习时间
record.setReviewStage(sm2.repetition);      // 兼容 UI 显示
```

> **重要设计：** 每次答题都创建新记录（不覆盖旧记录），  
> 下次查询时通过 `findTopByUserIdAndWordIdOrderByStudyTimeDesc` 取最新一条作为 SM-2 状态。

---

## 五、完整学习轨迹示例

以单词 "apple" 为例：

| 次序 | 答题 | q | EF（新） | repetition（新） | intervalDays | 下次复习 |
|---|---|---|---|---|---|---|
| 初始状态 | — | — | 2.50 | 0 | 0 | — |
| 第1次答对 | ✓ | 5 | 2.60 | 1 | 1 | 明天 |
| 第2次答对 | ✓ | 5 | 2.70 | 2 | 6 | 6天后 |
| 第3次答对 | ✓ | 5 | 2.80 | 3 | 17 | 17天后（6×2.8） |
| 第4次答对 | ✓ | 5 | 2.90 | 4 | 49 | 49天后（17×2.9） |
| 第5次**答错** | ✗ | 2 | 2.62 | 0 | 1 | 明天（**重置**） |
| 第6次答对 | ✓ | 5 | 2.72 | 1 | 1 | 明天 |
| 第7次答对 | ✓ | 5 | 2.82 | 2 | 6 | 6天后 |

---

## 六、相关文件索引

| 文件 | 作用 |
|---|---|
| `util/Sm2Scheduler.java` | SM-2 核心计算逻辑 |
| `entity/StudyRecord.java` | 存储 EF / repetition / intervalDays / nextReviewTime |
| `controller/StudyController.java` | 读取最新记录 → 调用 SM-2 → 写回新记录 |
| `repository/StudyRecordRepository.java` | `findTopByUserIdAndWordIdOrderByStudyTimeDesc` 查最新记录 |

---

## 七、可优化方向

1. **细化 q 映射**：目前只有 5/2 两档，可根据答题用时、拼写准确度等映射为 0~5 的多档评分
2. **Anki 改进版（SM-2+）**：对 q=3/4 引入不同 EF 调整幅度，使中等表现的单词间隔更合理
3. **最大间隔上限**：可设置 `intervalDays = min(intervalDays, 365)`，防止间隔过长导致遗忘
4. **学习曲线可视化**：在统计页面展示每个单词的 EF 和 repetition，让用户了解自己的记忆强度
