// pages/spelling-test/spelling-test.js
const app = getApp();

Page({
  data: {
    words: [],          // 本次测试的单词列表
    currentIndex: 0,
    currentWord: null,
    shownPart: '',      // 已显示部分
    blankPart: '',      // 需要填写部分
    blankDisplay: '',   // 下划线占位显示
    blankLength: 0,
    userInput: '',
    showResult: false,
    isCorrect: false,
    inputFocus: false,
    correctCount: 0,
    wrongCount: 0,
    wrongWords: [],     // 答错的单词
    finished: false,
    source: 'learn'     // 来源：learn 或 review
  },

  onLoad: function(options) {
    const source = options.source || 'learn';
    // 从全局拿本次学习/复习的单词列表
    const words = app.globalData.spellingTestWords || [];
    if (words.length === 0) {
      wx.showToast({ title: '没有可测试的单词', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1500);
      return;
    }
    this.setData({ words, source });
    this.loadWord(0);
  },

  loadWord: function(index) {
    const words = this.data.words;
    if (index >= words.length) {
      this.setData({ finished: true });
      return;
    }
    const word = words[index];
    const { shownPart, blankPart } = this.buildSpelling(word.word);
    const blankDisplay = '_'.repeat(blankPart.length);
    this.setData({
      currentIndex: index,
      currentWord: word,
      shownPart,
      blankPart,
      blankDisplay,
      blankLength: blankPart.length,
      userInput: '',
      showResult: false,
      isCorrect: false,
      inputFocus: true
    });
  },

  /**
   * 计算显示部分和空白部分
   * 规则：单词长度 <= 3 显示1个字母，4-5显示一半，6+显示60%
   * 空白部分为后面的字母
   */
  buildSpelling: function(word) {
    const len = word.length;
    let shownLen;
    if (len <= 3) shownLen = 1;
    else if (len <= 5) shownLen = Math.floor(len / 2);
    else shownLen = Math.ceil(len * 0.6);
    const shownPart = word.substring(0, shownLen);
    const blankPart = word.substring(shownLen);
    return { shownPart, blankPart };
  },

  onInput: function(e) {
    this.setData({ userInput: e.detail.value });
  },

  submitAnswer: function() {
    if (this.data.showResult) return;
    const userInput = this.data.userInput.trim().toLowerCase();
    const blankPart = this.data.blankPart.toLowerCase();
    if (!userInput) {
      wx.showToast({ title: '请输入缺失字母', icon: 'none' });
      return;
    }
    const isCorrect = userInput === blankPart;
    const newCorrect = this.data.correctCount + (isCorrect ? 1 : 0);
    const newWrong = this.data.wrongCount + (isCorrect ? 0 : 1);
    const wrongWords = isCorrect
      ? this.data.wrongWords
      : [...this.data.wrongWords, this.data.currentWord];
    this.setData({
      showResult: true,
      isCorrect,
      correctCount: newCorrect,
      wrongCount: newWrong,
      wrongWords,
      inputFocus: false
    });

    // 提交学习记录，让 SM-2 调度依据拼写结果更新
    this.submitSpellingRecordToBackend(this.data.currentWord, isCorrect);
  },

  nextWord: function() {
    const next = this.data.currentIndex + 1;
    if (next >= this.data.words.length) {
      this.setData({ finished: true });
    } else {
      this.loadWord(next);
    }
  },

  retryWrong: function() {
    const wrongWords = this.data.wrongWords;
    if (wrongWords.length === 0) return;
    app.globalData.spellingTestWords = wrongWords;
    this.setData({
      words: wrongWords,
      currentIndex: 0,
      correctCount: 0,
      wrongCount: 0,
      wrongWords: [],
      finished: false
    });
    this.loadWord(0);
  },

  goHome: function() {
    wx.switchTab({ url: '/pages/home/home' });
  },

  // ==================== 后端提交 ====================
  submitSpellingRecordToBackend: function(word, isCorrect) {
    const userInfo = app.globalData.userInfo;
    if (!userInfo || !word) return;

    const wordId = word.id || word.wordId;
    if (!wordId) return;

    const studyType = this.data.source === 'review' ? 'review' : 'new';
    const bktProbability = this.getMockBktProbability(word, isCorrect);
    app.submitStudyRecord({
      userId: userInfo.id,
      wordId: wordId,
      isCorrect: isCorrect,
      studyType: studyType,
      bktProbability: bktProbability
    }).catch(err => {
      console.error('拼写测试学习记录提交失败:', err);
    });
  },

  getMockBktProbability: function(word, isCorrect) {
    const difficulty = (word && word.difficulty) ? word.difficulty : 'medium';
    const baseMap = { easy: 0.85, medium: 0.70, hard: 0.55 };
    let p = baseMap[difficulty] || 0.70;
    if (isCorrect === true) p = Math.min(p + 0.10, 0.95);
    if (isCorrect === false) p = Math.max(p - 0.25, 0.10);
    return Number(p.toFixed(2));
  }
});
