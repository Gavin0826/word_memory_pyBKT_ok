// pages/review/review.js
const app = getApp();

Page({
  data: {
    userInfo: null,
    allReviewWords: [],
    reviewWords: [],
    loading: false,
    isFavorited: false,
    favoriteWordIds: [],

    // 复习会话状态
    isReviewing: false,
    sessionWords: [],
    currentIndex: 0,
    currentWord: null,
    reviewTestOptions: [],
    selectedOptionIndex: null,
    showAnswer: false,
    showResult: false,
    isCorrect: false,
    forgotWords: [],
    forgotRetryCount: {},   // 修复2：记录每个单词答错重试次数，最多重试2次

    // 统计
    totalReviewed: 0,
    correctCount: 0,
    reviewAccuracy: 0,

    // 列表展示
    isListExpanded: false,

    // 会话结束
    showSessionComplete: false,
    sessionCorrect: 0,
    sessionTotal: 0,
    sessionAccuracy: 0
  },

  onLoad: function() {},

  onShow: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.setData({ userInfo });
    this.loadFavoriteIds();
    this.loadReviewWords();
  },

  onHide: function() {
    if (this.data.isReviewing) this.exitReview();
  },

  // ==================== 收藏功能 ====================

  loadFavoriteIds: function() {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/favorite/' + userInfo.id + '/ids',
      method: 'GET',
      header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          const ids = (res.data.favoriteWordIds || []).map(id => Number(id));
          this.setData({ favoriteWordIds: ids });
        }
      }
    });
  },

  toggleFavorite: function() {
    const userInfo = this.data.userInfo;
    const currentWord = this.data.currentWord;
    if (!userInfo || !currentWord) return;
    const wordId = currentWord.id || currentWord.wordId;
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/favorite/toggle',
      method: 'POST',
      header: {
        'Authorization': 'Bearer ' + app.globalData.sessionToken,
        'content-type': 'application/json'
      },
      data: { userId: userInfo.id, wordId: wordId },
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          const favorited = res.data.favorited;
          let ids = [...this.data.favoriteWordIds];
          if (favorited) { ids.push(Number(wordId)); }
          else { ids = ids.filter(id => id !== Number(wordId)); }
          this.setData({ isFavorited: favorited, favoriteWordIds: ids });
          wx.showToast({ title: favorited ? '已收藏 ★' : '已取消收藏', icon: 'none', duration: 1000 });
        }
      },
      fail: () => wx.showToast({ title: '操作失败', icon: 'none' })
    });
  },

  // ==================== 数据加载 ====================

  loadReviewWords: function() {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    this.setData({ loading: true });
    app.getReviewWords(userInfo.id)
      .then(reviewRecords => {
        const words = this.processReviewWords(reviewRecords);
        this.setData({
          allReviewWords: words,
          reviewWords: words,
          loading: false
        });
      })
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  processReviewWords: function(reviewRecords) {
    if (!reviewRecords || !Array.isArray(reviewRecords)) return [];
    return reviewRecords.map(record => {
      const word = record.word || {};
      return {
        id: word.id,
        word: word.word || '未知单词',
        meaning: word.meaning || '未知释义',
        pronunciation: word.pronunciation || '',
        difficulty: word.difficulty || 'medium',
        reviewStage: record.reviewStage || 0,
        nextReviewTime: record.nextReviewTime,
        reviewTimeText: this.formatReviewTime(record.nextReviewTime),
        reviewStageText: this.getReviewStageText(record.reviewStage || 0)
      };
    });
  },

  // ==================== 开始复习 ====================

  startReview: function() {
    const { allReviewWords } = this.data;
    if (allReviewWords.length === 0) {
      wx.showToast({ title: '暂无需要复习的单词', icon: 'none' });
      return;
    }
    const perSession = (app.globalData.studySettings && app.globalData.studySettings.reviewWordsPerSession) || 10;
    const sessionWords = allReviewWords.slice(0, perSession);
    this.setData({
      isReviewing: true,
      sessionWords: [...sessionWords],
      currentIndex: 0,
      forgotWords: [],
      forgotRetryCount: {},
      showSessionComplete: false,
      sessionCorrect: 0,
      sessionTotal: 0,
      sessionAccuracy: 0,
      totalReviewed: 0,
      correctCount: 0,
      reviewAccuracy: 0
    });
    this.loadCurrentWord(0);
  },

  loadCurrentWord: function(index) {
    const { sessionWords, favoriteWordIds } = this.data;
    if (index >= sessionWords.length) {
      this.finishSession();
      return;
    }
    const word = sessionWords[index];
    const options = this.generateOptions(word, sessionWords);
    const wordId = Number(word.id || word.wordId);
    const isFav = favoriteWordIds.includes(wordId);
    this.setData({
      currentIndex: index,
      currentWord: word,
      reviewTestOptions: options,
      selectedOptionIndex: null,
      showAnswer: false,
      showResult: false,
      isCorrect: false,
      isFavorited: isFav
    });
  },

  // ==================== 选项与答题 ====================

  generateOptions: function(correctWord, pool) {
    const options = [correctWord.meaning];
    const others = pool.filter(w => w.id !== correctWord.id);
    const shuffled = this.shuffleArray([...others]);
    const fallback = ['学习', '记忆', '掌握', '练习', '理解', '忘记', '复习', '进步'];
    if (shuffled.length >= 3) {
      for (let i = 0; i < 3; i++) options.push(shuffled[i].meaning);
    } else {
      for (let i = 0; i < shuffled.length; i++) options.push(shuffled[i].meaning);
      const need = 3 - shuffled.length;
      for (let i = 0; i < need; i++) options.push(fallback[i]);
    }
    return this.shuffleArray(options);
  },

  selectOption: function(e) {
    if (this.data.showResult || this.data.showAnswer) return;
    this.setData({ selectedOptionIndex: e.currentTarget.dataset.index });
  },

  submitAnswer: function() {
    const { currentWord, selectedOptionIndex, reviewTestOptions } = this.data;
    if (selectedOptionIndex === null) {
      wx.showToast({ title: '请选择一个答案', icon: 'none' });
      return;
    }
    const isCorrect = reviewTestOptions[selectedOptionIndex] === currentWord.meaning;
    const newCorrect = this.data.correctCount + (isCorrect ? 1 : 0);
    const newTotal = this.data.totalReviewed + 1;
    this.setData({
      isCorrect,
      showResult: true,
      correctCount: newCorrect,
      totalReviewed: newTotal,
      reviewAccuracy: Math.round(newCorrect / newTotal * 100)
    });
    this.submitRecordToBackend(currentWord, isCorrect);
  },

  nextWord: function() {
    const { currentIndex, isCorrect, currentWord, sessionWords, forgotWords, forgotRetryCount } = this.data;
    if (!isCorrect) {
      const wordId = currentWord.id;
      const retryCount = forgotRetryCount[wordId] || 0;
      const MAX_RETRY = 2; // 修复2：每个单词最多重试2次
      if (retryCount < MAX_RETRY) {
        // 还有重试机会：追加到末尾
        const alreadyForgot = forgotWords.some(w => w.id === wordId);
        const newForgotWords = alreadyForgot ? forgotWords : [...forgotWords, currentWord];
        const newSession = [...sessionWords, currentWord];
        const newRetryCount = { ...forgotRetryCount, [wordId]: retryCount + 1 };
        this.setData({
          forgotWords: newForgotWords,
          sessionWords: newSession,
          forgotRetryCount: newRetryCount
        });
      }
      // 超过最大重试次数：不再追加，直接跳过
    }
    this.loadCurrentWord(currentIndex + 1);
  },

  // ==================== 忘记功能 ====================

  onForgot: function() {
    const { currentWord, sessionWords, forgotWords } = this.data;
    const alreadyForgot = forgotWords.some(w => w.id === currentWord.id);
    const newForgotWords = alreadyForgot ? forgotWords : [...forgotWords, currentWord];
    const newSession = [...sessionWords, currentWord];
    this.setData({
      showAnswer: true,
      forgotWords: newForgotWords,
      sessionWords: newSession
    });
    this.submitRecordToBackend(currentWord, false);
  },

  continueAfterForgot: function() {
    const { currentIndex } = this.data;
    this.loadCurrentWord(currentIndex + 1);
  },

  // ==================== 退出复习 ====================

  exitReview: function() {
    wx.showModal({
      title: '退出复习',
      content: '确定退出本次复习？当前进度将不会保存为完成。',
      success: (res) => {
        if (res.confirm) {
          this.setData({
            isReviewing: false,
            sessionWords: [],
            currentWord: null,
            showSessionComplete: false
          });
          this.loadReviewWords();
        }
      }
    });
  },

  // ==================== 完成本组 ====================

  finishSession: function() {
    const { totalReviewed, correctCount, sessionWords } = this.data;
    const sessionAccuracy = totalReviewed > 0 ? Math.round(correctCount / totalReviewed * 100) : 0;
    // 收集本组复习单词（去重）传给拼写测试
    const seen = new Set();
    const spellingWords = (sessionWords || []).filter(w => {
      if (seen.has(w.id)) return false;
      seen.add(w.id); return true;
    }).map(w => ({
      id: w.id, word: w.word,
      meaning: w.meaning,
      pronunciation: w.pronunciation || ''
    }));
    if (spellingWords.length > 0) {
      app.globalData.spellingTestWords = spellingWords;
    }
    this.setData({
      isReviewing: false,
      showSessionComplete: true,
      sessionTotal: totalReviewed,
      sessionCorrect: correctCount,
      sessionAccuracy: sessionAccuracy
    });
    setTimeout(() => {
      this.setData({ showSessionComplete: false });
      // 修复3：重新从后端拉取复习词，让 SM-2 的新 nextReviewTime 生效，不再用本地 filter 保留答错词
      this.loadReviewWords();
      if (spellingWords.length > 0) {
        wx.navigateTo({ url: '/pages/spelling-test/spelling-test?source=review' });
      }
    }, 2000);
  },

  closeSessionComplete: function() {
    // 修复3：关闭结算弹窗时也重新从后端拉取，保证SM-2调度状态最新
    this.setData({ showSessionComplete: false });
    this.loadReviewWords();
  },

  // ==================== 后端提交 ====================

  submitRecordToBackend: function(word, isCorrect) {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    const bktProbability = this.getMockBktProbability(word, isCorrect);
    app.submitStudyRecord({
      userId: userInfo.id,
      wordId: word.id,
      isCorrect: isCorrect,
      studyType: 'review',
      bktProbability: bktProbability
    }).catch(err => console.error('学习记录提交失败:', err));
  },

  getMockBktProbability: function(word, isCorrect) {
    const difficulty = (word && word.difficulty) ? word.difficulty : 'medium';
    const baseMap = { easy: 0.85, medium: 0.70, hard: 0.55 };
    let p = baseMap[difficulty] || 0.70;
    if (isCorrect === true) p = Math.min(p + 0.10, 0.95);
    if (isCorrect === false) p = Math.max(p - 0.25, 0.10);
    return Number(p.toFixed(2));
  },

  // ==================== 列表控制 ====================

  toggleList: function() {
    this.setData({ isListExpanded: !this.data.isListExpanded });
  },

  startSingleWord: function(e) {
    const word = e.currentTarget.dataset.word;
    if (!word) return;
    const options = this.generateOptions(word, this.data.allReviewWords);
    this.setData({
      isReviewing: true,
      sessionWords: [word],
      currentIndex: 0,
      currentWord: word,
      reviewTestOptions: options,
      selectedOptionIndex: null,
      showAnswer: false,
      showResult: false,
      forgotWords: [],
      showSessionComplete: false,
      sessionCorrect: 0,
      sessionTotal: 0,
      sessionAccuracy: 0
    });
  },

  // ==================== 工具方法 ====================

  formatReviewTime: function(nextReviewTime) {
    if (!nextReviewTime) return '需要尽快复习';
    try {
      const diffMs = new Date(nextReviewTime) - new Date();
      if (diffMs <= 0) return '需要尽快复习';
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      if (diffDays > 0) return diffDays + '天后复习';
      if (diffHours > 0) return diffHours + '小时后复习';
      const diffMinutes = Math.floor(diffMs / (1000 * 60));
      return diffMinutes > 0 ? diffMinutes + '分钟后复习' : '即将复习';
    } catch (e) { return '需要复习'; }
  },

  getReviewStageText: function(stage) {
    const stages = ['新学单词', '第1次复习', '第2次复习', '第3次复习'];
    if (stage >= 4 && stage <= 6) return '强化记忆';
    if (stage >= 7) return '长期记忆';
    return stages[stage] || '新学单词';
  },

  getDifficultyText: function(difficulty) {
    const m = { easy: '简单', medium: '中等', hard: '困难' };
    return m[difficulty] || '中等';
  },

  getReviewTimeColor: function(text) {
    if (!text) return '#757575';
    if (text.indexOf('尽快') >= 0 || text.indexOf('已过期') >= 0) return '#F44336';
    if (text.indexOf('分钟') >= 0 || text.indexOf('小时') >= 0) return '#FF9800';
    return '#4CAF50';
  },

  shuffleArray: function(arr) {
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      var tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
    }
    return arr;
  },

  goToStudy: function() {
    wx.switchTab({ url: '/pages/study/study' });
  },

  goToLogin: function() {
    wx.redirectTo({ url: '/pages/login/login' });
  }
});
