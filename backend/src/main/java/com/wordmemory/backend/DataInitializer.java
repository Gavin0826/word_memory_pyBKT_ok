package com.wordmemory.backend;

import com.wordmemory.backend.entity.User;
import com.wordmemory.backend.entity.Word;
import com.wordmemory.backend.repository.UserRepository;
import com.wordmemory.backend.repository.WordRepository;
import com.wordmemory.backend.util.PasswordUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WordRepository wordRepository;

    public DataInitializer(UserRepository userRepository, WordRepository wordRepository) {
        this.userRepository = userRepository;
        this.wordRepository = wordRepository;
    }

    @Override
    public void run(String... args) {
        initUsers();
        initWords();
    }

    private void initUsers() {
        if (userRepository.count() == 0) {
            // 创建测试用户
            User testUser = new User();
            testUser.setUsername("test");
            testUser.setEmail("test@example.com");
            testUser.setPasswordHash(PasswordUtil.encryptPassword("123456"));
            testUser.setTotalWords(0);
            testUser.setStudiedDays(0);
            testUser.setLastLogin(LocalDateTime.now());

            userRepository.save(testUser);
            System.out.println("✅ 测试用户创建成功");
            System.out.println("  用户名: test");
            System.out.println("  密码: 123456");
            System.out.println("  邮箱: test@example.com");
        }
    }

    private void initWords() {
        // 词库数据已通过 import_words.sql 从本地词典导入，不再使用手动初始化
        // 如需重置词库，请重新执行 import_words.sql
        long wordCount = wordRepository.count();
        if (wordCount > 0) {
            List<Word> allWords = wordRepository.findAll();
            long cet4Count = allWords.stream().filter(w -> "CET-4".equals(w.getCategory())).count();
            long cet6Count = allWords.stream().filter(w -> "CET-6".equals(w.getCategory())).count();
            System.out.println("📚 当前词库统计：");
            System.out.println("  CET-4: " + cet4Count + " 个单词");
            System.out.println("  CET-6: " + cet6Count + " 个单词");
            System.out.println("  总计: " + wordCount + " 个单词");
            return;
        }
        // 词库为空时的提示
        System.out.println("⚠️  词库为空！请执行 import_words.sql 导入词库数据。");
        System.out.println("   方法: mysql -u root -p word_memory_db < import_words.sql");
        if (false) { // 以下为旧版手动词库，已弃用
            List<Word> words = new ArrayList<>();

            // CET-4 单词（扩展至30个）
            words.add(createWord("apple", "/ˈæpl/", "苹果", "easy", "CET-4"));
            words.add(createWord("book", "/bʊk/", "书", "easy", "CET-4"));
            words.add(createWord("computer", "/kəmˈpjuːtər/", "计算机", "medium", "CET-4"));
            words.add(createWord("develop", "/dɪˈveləp/", "发展", "medium", "CET-4"));
            words.add(createWord("education", "/ˌedʒuˈkeɪʃn/", "教育", "medium", "CET-4"));
            words.add(createWord("future", "/ˈfjuːtʃər/", "未来", "easy", "CET-4"));
            words.add(createWord("government", "/ˈɡʌvərnmənt/", "政府", "medium", "CET-4"));
            words.add(createWord("history", "/ˈhɪstri/", "历史", "easy", "CET-4"));
            words.add(createWord("important", "/ɪmˈpɔːrtnt/", "重要的", "medium", "CET-4"));
            words.add(createWord("knowledge", "/ˈnɑːlɪdʒ/", "知识", "medium", "CET-4"));
            words.add(createWord("language", "/ˈlæŋɡwɪdʒ/", "语言", "medium", "CET-4"));
            words.add(createWord("market", "/ˈmɑːrkɪt/", "市场", "easy", "CET-4"));
            words.add(createWord("nature", "/ˈneɪtʃər/", "自然", "medium", "CET-4"));
            words.add(createWord("opinion", "/əˈpɪnjən/", "观点", "medium", "CET-4"));
            words.add(createWord("people", "/ˈpiːpl/", "人们", "easy", "CET-4"));
            words.add(createWord("question", "/ˈkwestʃən/", "问题", "easy", "CET-4"));
            words.add(createWord("research", "/ˈriːsɜːrtʃ/", "研究", "medium", "CET-4"));
            words.add(createWord("science", "/ˈsaɪəns/", "科学", "medium", "CET-4"));
            words.add(createWord("technology", "/tekˈnɑːlədʒi/", "技术", "medium", "CET-4"));
            words.add(createWord("university", "/ˌjuːnɪˈvɜːrsəti/", "大学", "medium", "CET-4"));
            words.add(createWord("village", "/ˈvɪlɪdʒ/", "村庄", "easy", "CET-4"));
            words.add(createWord("water", "/ˈwɔːtər/", "水", "easy", "CET-4"));
            words.add(createWord("year", "/jɪr/", "年", "easy", "CET-4"));
            words.add(createWord("zoo", "/zuː/", "动物园", "easy", "CET-4"));
            words.add(createWord("ability", "/əˈbɪləti/", "能力", "medium", "CET-4"));
            words.add(createWord("business", "/ˈbɪznəs/", "商业", "medium", "CET-4"));
            words.add(createWord("challenge", "/ˈtʃælɪndʒ/", "挑战", "medium", "CET-4"));
            words.add(createWord("decision", "/dɪˈsɪʒn/", "决定", "medium", "CET-4"));
            words.add(createWord("experience", "/ɪkˈspɪriəns/", "经验", "medium", "CET-4"));
            words.add(createWord("freedom", "/ˈfriːdəm/", "自由", "medium", "CET-4"));

            // CET-6 单词（扩展至30个）
            words.add(createWord("abandon", "/əˈbændən/", "放弃", "medium", "CET-6"));
            words.add(createWord("accommodate", "/əˈkɒmədeɪt/", "容纳", "hard", "CET-6"));
            words.add(createWord("benevolent", "/bəˈnevələnt/", "仁慈的", "hard", "CET-6"));
            words.add(createWord("criterion", "/kraɪˈtɪriən/", "标准", "hard", "CET-6"));
            words.add(createWord("dilemma", "/dɪˈlemə/", "困境", "medium", "CET-6"));
            words.add(createWord("eloquent", "/ˈeləkwənt/", "雄辩的", "hard", "CET-6"));
            words.add(createWord("facilitate", "/fəˈsɪlɪteɪt/", "促进", "hard", "CET-6"));
            words.add(createWord("gregarious", "/ɡrɪˈɡeriəs/", "社交的", "hard", "CET-6"));
            words.add(createWord("hypothesis", "/haɪˈpɒθəsɪs/", "假设", "hard", "CET-6"));
            words.add(createWord("ignite", "/ɪɡˈnaɪt/", "点燃", "medium", "CET-6"));
            words.add(createWord("juxtapose", "/ˈdʒʌkstəpəʊz/", "并列", "hard", "CET-6"));
            words.add(createWord("lucid", "/ˈluːsɪd/", "清晰的", "hard", "CET-6"));
            words.add(createWord("magnanimous", "/mæɡˈnænɪməs/", "宽宏大量的", "hard", "CET-6"));
            words.add(createWord("nostalgia", "/nɒˈstældʒə/", "怀旧", "medium", "CET-6"));
            words.add(createWord("obsolete", "/ˈɒbsəliːt/", "过时的", "hard", "CET-6"));
            words.add(createWord("paradox", "/ˈpærədɒks/", "悖论", "hard", "CET-6"));
            words.add(createWord("quintessential", "/ˌkwɪntɪˈsenʃl/", "典型的", "hard", "CET-6"));
            words.add(createWord("resilient", "/rɪˈzɪliənt/", "有弹性的", "hard", "CET-6"));
            words.add(createWord("sycophant", "/ˈsɪkəfænt/", "马屁精", "hard", "CET-6"));
            words.add(createWord("taciturn", "/ˈtæsɪtɜːn/", "沉默寡言的", "hard", "CET-6"));
            words.add(createWord("ubiquitous", "/juːˈbɪkwɪtəs/", "无处不在的", "hard", "CET-6"));
            words.add(createWord("vindicate", "/ˈvɪndɪkeɪt/", "证明正确", "hard", "CET-6"));
            words.add(createWord("witty", "/ˈwɪti/", "机智的", "medium", "CET-6"));
            words.add(createWord("xenophobia", "/ˌzenəˈfəʊbiə/", "仇外心理", "hard", "CET-6"));
            words.add(createWord("yield", "/jiːld/", "产量", "medium", "CET-6"));
            words.add(createWord("zeal", "/ziːl/", "热情", "medium", "CET-6"));
            words.add(createWord("ambiguous", "/æmˈbɪɡjuəs/", "模糊的", "hard", "CET-6"));
            words.add(createWord("bureaucracy", "/bjʊˈrɒkrəsi/", "官僚主义", "hard", "CET-6"));
            words.add(createWord("cumbersome", "/ˈkʌmbəsəm/", "笨重的", "hard", "CET-6"));
            words.add(createWord("dichotomy", "/daɪˈkɒtəmi/", "二分法", "hard", "CET-6"));
            words.add(createWord("ephemeral", "/ɪˈfemərəl/", "短暂的", "hard", "CET-6"));

            // 考研单词（扩展至30个）
            words.add(createWord("algorithm", "/ˈælɡərɪðəm/", "算法", "hard", "考研"));
            words.add(createWord("bibliography", "/ˌbɪbliˈɒɡrəfi/", "参考文献", "hard", "考研"));
            words.add(createWord("cognition", "/kɒɡˈnɪʃn/", "认知", "hard", "考研"));
            words.add(createWord("deduction", "/dɪˈdʌkʃn/", "演绎", "hard", "考研"));
            words.add(createWord("empirical", "/ɪmˈpɪrɪkl/", "经验主义的", "hard", "考研"));
            words.add(createWord("formulate", "/ˈfɔːmjuleɪt/", "制定", "hard", "考研"));
            words.add(createWord("genre", "/ˈʒɑːnrə/", "类型", "medium", "考研"));
            words.add(createWord("hierarchy", "/ˈhaɪərɑːki/", "等级制度", "hard", "考研"));
            words.add(createWord("ideology", "/ˌaɪdiˈɒlədʒi/", "意识形态", "hard", "考研"));
            words.add(createWord("jurisdiction", "/ˌdʒʊərɪsˈdɪkʃn/", "司法权", "hard", "考研"));
            words.add(createWord("kinetics", "/kɪˈnetɪks/", "动力学", "hard", "考研"));
            words.add(createWord("lexicon", "/ˈleksɪkən/", "词汇", "hard", "考研"));
            words.add(createWord("metaphor", "/ˈmetəfə(r)/", "隐喻", "medium", "考研"));
            words.add(createWord("normative", "/ˈnɔːmətɪv/", "规范的", "hard", "考研"));
            words.add(createWord("ontology", "/ɒnˈtɒlədʒi/", "本体论", "hard", "考研"));
            words.add(createWord("paradigm", "/ˈpærədaɪm/", "范式", "hard", "考研"));
            words.add(createWord("quantum", "/ˈkwɒntəm/", "量子", "hard", "考研"));
            words.add(createWord("rhetoric", "/ˈretərɪk/", "修辞", "hard", "考研"));
            words.add(createWord("synthesis", "/ˈsɪnθəsɪs/", "合成", "hard", "考研"));
            words.add(createWord("taxonomy", "/tækˈsɒnəmi/", "分类学", "hard", "考研"));
            words.add(createWord("utilitarian", "/ˌjuːtɪlɪˈteəriən/", "功利主义的", "hard", "考研"));
            words.add(createWord("validate", "/ˈvælɪdeɪt/", "验证", "hard", "考研"));
            words.add(createWord("warrant", "/ˈwɒrənt/", "授权", "medium", "考研"));
            words.add(createWord("xenotransplantation", "/ˌzenəʊtrænsplɑːnˈteɪʃn/", "异种移植", "hard", "考研"));
            words.add(createWord("yield", "/jiːld/", "屈服", "medium", "考研"));
            words.add(createWord("zeitgeist", "/ˈzaɪtɡaɪst/", "时代精神", "hard", "考研"));
            words.add(createWord("aesthetic", "/iːsˈθetɪk/", "美学的", "hard", "考研"));
            words.add(createWord("bilateral", "/ˌbaɪˈlætərəl/", "双边的", "hard", "考研"));
            words.add(createWord("cohesion", "/kəʊˈhiːʒn/", "凝聚力", "hard", "考研"));
            words.add(createWord("demographic", "/ˌdeməˈɡræfɪk/", "人口的", "hard", "考研"));
            words.add(createWord("epistemology", "/ɪˌpɪstəˈmɒlədʒi/", "认识论", "hard", "考研"));

            wordRepository.saveAll(words);

            // 统计信息
            long cet4Count = words.stream().filter(w -> "CET-4".equals(w.getCategory())).count();
            long cet6Count = words.stream().filter(w -> "CET-6".equals(w.getCategory())).count();
            long kaoyanCount = words.stream().filter(w -> "考研".equals(w.getCategory())).count();

            System.out.println("✅ 词库初始化完成");
            System.out.println("📊 词库分布：");
            System.out.println("  CET-4: " + cet4Count + "个单词");
            System.out.println("  CET-6: " + cet6Count + "个单词");
            System.out.println("  考研: " + kaoyanCount + "个单词");
            System.out.println("  总计: " + words.size() + "个单词");
        } else {
            // 显示当前词库统计
            List<Word> allWords = wordRepository.findAll();
            long cet4Count = allWords.stream().filter(w -> "CET-4".equals(w.getCategory())).count();
            long cet6Count = allWords.stream().filter(w -> "CET-6".equals(w.getCategory())).count();
            long kaoyanCount = allWords.stream().filter(w -> "考研".equals(w.getCategory())).count();

            System.out.println("📊 现有词库统计：");
            System.out.println("  CET-4: " + cet4Count + "个单词");
            System.out.println("  CET-6: " + cet6Count + "个单词");
            System.out.println("  考研: " + kaoyanCount + "个单词");
            System.out.println("  总计: " + allWords.size() + "个单词");
        }
    }

    private Word createWord(String word, String pronunciation, String meaning,
                            String difficulty, String category) {
        Word w = new Word();
        w.setWord(word);
        w.setPronunciation(pronunciation);
        w.setMeaning(meaning);
        w.setDifficulty(difficulty);
        w.setCategory(category);
        return w;
    }
}