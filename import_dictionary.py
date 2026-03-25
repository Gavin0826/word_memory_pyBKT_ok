#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地词典导入脚本
将 CET-4 和 CET-6 词典 JSON 文件解析并生成 MySQL 导入 SQL

使用方法：
  python import_dictionary.py

生成文件：
  import_words.sql  -- 在 MySQL 中执行此文件即可导入词库
"""

import json
import os
import re

# 词典文件路径
CET4_FILE = os.path.join(os.path.dirname(__file__), 'dictionary', 'CET4luan_2', 'CET4luan_2.json')
CET6_FILE = os.path.join(os.path.dirname(__file__), 'dictionary', 'CET6_2', 'CET6_2.json')
OUTPUT_SQL = os.path.join(os.path.dirname(__file__), 'import_words.sql')


def escape_sql(s):
    """转义 SQL 字符串中的特殊字符"""
    if s is None:
        return ''
    s = str(s)
    s = s.replace('\\', '\\\\')
    s = s.replace("'", "\\'")  
    s = s.replace('\n', ' ')
    s = s.replace('\r', ' ')
    return s


def parse_word(line, category):
    """
    解析单行词典 JSON，提取所需字段。
    返回 dict 或 None（跳过无效行）
    """
    line = line.strip()
    if not line:
        return None
    try:
        obj = json.loads(line)
    except json.JSONDecodeError:
        return None

    head_word = obj.get('headWord', '')
    if not head_word:
        return None

    content = obj.get('content', {})
    word_obj = content.get('word', {})
    word_content = word_obj.get('content', {})

    # 音标：优先美式，其次英式
    pronunciation = (
        word_content.get('usphone') or
        word_content.get('ukphone') or
        word_content.get('phone') or
        ''
    )
    if pronunciation and not pronunciation.startswith('/'):
        pronunciation = '/' + pronunciation + '/'

    # 释义：取第一条中文释义
    meaning = ''
    trans = word_content.get('trans', [])
    if trans:
        parts = []
        for t in trans:
            tran_cn = t.get('tranCn', '').strip()
            pos = t.get('pos', '').strip()
            if tran_cn:
                if pos:
                    parts.append(f'{pos}. {tran_cn}')
                else:
                    parts.append(tran_cn)
        meaning = '；'.join(parts[:3])  # 最多取3个释义

    if not meaning:
        return None

    # 难度：根据 star 字段判断（0-2: easy, 3-4: medium, 5: hard）
    star = word_content.get('star', 0)
    if star is None:
        star = 0
    if star <= 1:
        difficulty = 'easy'
    elif star <= 3:
        difficulty = 'medium'
    else:
        difficulty = 'hard'

    # 截断过长的释义
    if len(meaning) > 200:
        meaning = meaning[:200]

    return {
        'word': head_word,
        'pronunciation': pronunciation[:100] if pronunciation else '',
        'meaning': meaning,
        'difficulty': difficulty,
        'category': category,
    }


def parse_file(filepath, category):
    """解析整个词典文件，返回单词列表"""
    words = []
    seen = set()  # 去重
    
    print(f'正在解析 {category} 词典: {filepath}')
    
    with open(filepath, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            word_data = parse_word(line, category)
            if word_data is None:
                continue
            # 同一词库内去重（同一单词只保留第一次出现）
            key = word_data['word'].lower()
            if key in seen:
                continue
            seen.add(key)
            words.append(word_data)
    
    print(f'  成功解析 {len(words)} 个单词')
    return words


def generate_sql(all_words, output_path):
    """生成 SQL 文件"""
    cet4 = [w for w in all_words if w['category'] == 'CET-4']
    cet6 = [w for w in all_words if w['category'] == 'CET-6']

    with open(output_path, 'w', encoding='utf-8', errors='replace') as f:
        f.write('-- ============================================================\n')
        f.write('-- 单词记忆系统 - 词库导入 SQL\n')
        f.write(f'-- CET-4: {len(cet4)} 个单词\n')
        f.write(f'-- CET-6: {len(cet6)} 个单词\n')
        f.write(f'-- 总计:  {len(all_words)} 个单词\n')
        f.write('-- ============================================================\n\n')

        f.write('USE word_memory_db;\n\n')

        # 关键：设置字符集和禁用外键检查
        f.write('-- 设置字符集为 utf8mb4（解决中文乱码）\n')
        f.write('SET NAMES utf8mb4;\n')
        f.write('SET CHARACTER SET utf8mb4;\n')
        f.write('SET character_set_connection=utf8mb4;\n\n')

        f.write('-- 禁用外键检查（解决 study_record 外键约束问题）\n')
        f.write('SET FOREIGN_KEY_CHECKS = 0;\n\n')

        # 清空旧词库数据（保留用户数据和学习记录）
        f.write('-- 清空旧词库（保留用户数据和学习记录）\n')
        f.write('DELETE FROM word;\n')
        f.write('ALTER TABLE word AUTO_INCREMENT = 1;\n\n')

        f.write('-- 重新启用外键检查\n')
        f.write('SET FOREIGN_KEY_CHECKS = 1;\n\n')

        # 分批插入，每批 500 条
        batch_size = 500
        total = len(all_words)
        batches = (total + batch_size - 1) // batch_size

        f.write(f'-- 共 {total} 个单词，分 {batches} 批插入\n')
        f.write('-- 字段顺序: word, pronunciation, meaning, difficulty, category\n\n')

        for i in range(0, total, batch_size):
            batch = all_words[i:i + batch_size]
            batch_num = i // batch_size + 1
            f.write(f'-- 第 {batch_num}/{batches} 批\n')
            f.write('INSERT INTO word (word, pronunciation, meaning, difficulty, category) VALUES\n')
            
            rows = []
            for w in batch:
                word_esc = escape_sql(w['word'])
                pron_esc = escape_sql(w['pronunciation'])
                mean_esc = escape_sql(w['meaning'])
                diff_esc = escape_sql(w['difficulty'])
                cat_esc  = escape_sql(w['category'])
                rows.append(f"  ('{word_esc}', '{pron_esc}', '{mean_esc}', '{diff_esc}', '{cat_esc}')")
            
            f.write(',\n'.join(rows))
            f.write(';\n\n')

        f.write('-- 验证导入结果\n')
        f.write('SELECT category, COUNT(*) as count FROM word GROUP BY category;\n')
        f.write('SELECT COUNT(*) as total FROM word;\n')

    print(f'\nSQL 文件已生成: {output_path}')


def main():
    print('=' * 50)
    print('单词记忆系统 - 词典导入工具')
    print('=' * 50)

    # 检查文件是否存在
    for path, name in [(CET4_FILE, 'CET-4'), (CET6_FILE, 'CET-6')]:
        if not os.path.exists(path):
            print(f'错误：找不到 {name} 词典文件: {path}')
            return

    all_words = []

    # 解析 CET-4
    cet4_words = parse_file(CET4_FILE, 'CET-4')
    all_words.extend(cet4_words)

    # 解析 CET-6
    cet6_words = parse_file(CET6_FILE, 'CET-6')
    all_words.extend(cet6_words)

    print(f'\n共解析 {len(all_words)} 个单词')
    print(f'  CET-4: {len(cet4_words)} 个')
    print(f'  CET-6: {len(cet6_words)} 个')

    # 生成 SQL
    generate_sql(all_words, OUTPUT_SQL)

    print('\n' + '=' * 50)
    print('下一步操作：')
    print('  在 MySQL 中执行生成的 import_words.sql 文件')
    print('  方法1（命令行）：')
    print('    mysql -u root -p word_memory_db < import_words.sql')
    print('  方法2（MySQL Workbench）：')
    print('    打开 import_words.sql → 点击执行按钮')
    print('  方法3（命令行交互）：')
    print('    mysql -u root -p')
    print('    source import_words.sql')
    print('=' * 50)


if __name__ == '__main__':
    main()
