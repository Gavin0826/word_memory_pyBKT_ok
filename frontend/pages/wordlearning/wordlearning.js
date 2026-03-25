// pages/wordlearning/wordlearning.js
const app = getApp();

Page({
  data: {
    stage: 1,
    category: '',
    allWords: [],
    knownWords: [],
    unknownWords: [],
    currentWordIndex: 0,
    totalWords: 0,
    currentWord: null,
    showMeaning: false,
    isKnown: false,
    testWords: [],
    testWordIndex: 0,
    testOptions: [],
    selectedOption: null,
    showTestResult: false,
    isTestCorrect: false,
    testRetryCount: {},
    maxRetries: 3,
    progress: 0,
    knownCount: 0,
    unknownCount: 0,
    testedCount: 0,
    loading: false,
    isFavorited: false,
    favoriteWordIds: []
  },

  onLoad: function(options) {
    const category = options.category;
    if (!category) {
      wx.showToast({ title: '请选择词库', icon: 'none' });
      wx.navigateBack();
      return;
    }
    this.setData({ category: category, loading: true });
    this.loadFavoriteIds();
    this.loadLearningTask();
  },

  loadFavoriteIds: function() {
    const userInfo = app.globalData.userInfo;
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
    const userInfo = app.globalData.userInfo;
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
          if (favorited) {
            if (!ids.includes(Number(wordId))) ids.push(Number(wordId));
          } else {
            ids = ids.filter(id => id !== Number(wordId));
          }
          this.setData({ isFavorited: favorited, favoriteWordIds: ids });
          wx.showToast({ title: favorited ? '已收藏 ★' : '已取消收藏', icon: 'none', duration: 1000 });
        }
      },
      fail: () => wx.showToast({ title: '操作失败', icon: 'none' })
    });
  },

  loadLearningTask: function() {
    const that = this;
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/category-task',
      method: 'GET',
      header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
      data: {
        userId: userInfo.id,
        category: this.data.category,
        newWordsCount: (app.globalData.studySettings && app.globalData.studySettings.newWordsPerDay) || 10
      },
      success: function(res) {
        that.setData({ loading: false });
        if (res.data.status === 'success') {
          const allWords = [];
          if (res.data.reviewWords) allWords.push(...res.data.reviewWords);
          if (res.data.newWords) allWords.push(...res.data.newWords);
          const shuffledWords = that.shuffleArray([...allWords]);
          const firstWord = shuffledWords[0] || null;
          const firstId = firstWord ? Number(firstWord.id || firstWord.wordId) : null;
          const isFav = firstId ? that.data.favoriteWordIds.includes(firstId) : false;
          that.setData({
            allWords: shuffledWords,
            totalWords: shuffledWords.length,
            currentWord: firstWord,
            progress: shuffledWords.length > 0 ? 1 : 0,
            isFavorited: isFav
          });
          if (shuffledWords.length === 0) that.showCompletion();
        } else {
          wx.showToast({ title: res.data.message || '加载失败', icon: 'none' });
        }
      },
      fail: function() {
        that.setData({ loading: false });
        wx.showToast({ title: '网络错误', icon: 'none' });
      }
    });
  },

  onKnow: function() {
    const currentWord = this.data.currentWord;
    if (!currentWord) return;
    // 修复5：onKnow 不再立即提交 SM-2 记录，避免与测试阶段重复提交
    // 只在测试阶段（selectTestOption）提交，对于直接认识的单词在 nextWord 后提交
    this.setData({
      knownWords: [...this.data.knownWords, currentWord],
      knownCount: this.data.knownCount + 1,
      isKnown: true,
      showMeaning: true
    });
    this.updateProgress();
  },

  onUnknown: function() {
    const currentWord = this.data.currentWord;
    if (!currentWord) return;
    // 修复5：onUnknown 不立即提交，等测试阶段答题结果提交（test_correct/test_wrong）
    this.setData({
      unknownWords: [...this.data.unknownWords, currentWord],
      unknownCount: this.data.unknownCount + 1,
      isKnown: false,
      showMeaning: true
    });
    this.updateProgress();
  },

  nextWord: function() {
    const currentWordIndex = this.data.currentWordIndex;
    const allWords = this.data.allWords;
    const isKnown = this.data.isKnown;
    const currentWord = this.data.currentWord;
    if (isKnown) {
      // 修复5：认识的单词在跳过时才提交 know，避免与测试阶段重复
      this.submitLearningRecord(currentWord.id, 'know');
    } else {
      this.setData({ testWords: [...this.data.testWords, currentWord] });
    }
    const nextIndex = currentWordIndex + 1;
    if (nextIndex < allWords.length) {
      const nextWord = allWords[nextIndex];
      const nextId = Number(nextWord.id || nextWord.wordId);
      const isFav = this.data.favoriteWordIds.includes(nextId);
      this.setData({
        currentWordIndex: nextIndex,
        currentWord: nextWord,
        showMeaning: false,
        isKnown: false,
        isFavorited: isFav
      });
      this.updateProgress();
    } else {
      this.startTestStage();
    }
  },

  startTestStage: function() {
    if (this.data.testWords.length === 0) {
      this.showCompletion();
      return;
    }
    this.setData({ stage: 2, testWordIndex: 0 });
    this.loadTestWord(0);
  },

  loadTestWord: function(index) {
    const testWords = this.data.testWords;
    if (index >= testWords.length) {
      this.showCompletion();
      return;
    }
    const currentTestWord = testWords[index];
    const options = this.generateTestOptions(currentTestWord);
    const testId = Number(currentTestWord.id || currentTestWord.wordId);
    const isFav = this.data.favoriteWordIds.includes(testId);
    this.setData({
      currentWord: currentTestWord,
      testWordIndex: index,
      testOptions: options,
      selectedOption: null,
      showTestResult: false,
      isFavorited: isFav
    });
  },

  // Bug7 fixed: use allWords pool as fallback instead of unrelated Chinese words
  generateTestOptions: function(correctWord) {
    const testWords = this.data.testWords;
    const allWords = this.data.allWords;
    const options = [correctWord.meaning];
    const otherTestWords = testWords.filter(function(w) { return w.id !== correctWord.id; });

    if (otherTestWords.length >= 3) {
      const shuffled = this.shuffleArray([...otherTestWords]);
      for (var i = 0; i < 3; i++) {
        if (shuffled[i]) options.push(shuffled[i].meaning);
      }
    } else {
      // fill from otherTestWords first
      for (var j = 0; j < otherTestWords.length; j++) {
        options.push(otherTestWords[j].meaning);
      }
      // then fill from allWords pool
      var need = 3 - otherTestWords.length;
      var fallbackPool = allWords.filter(function(w) {
        return w.id !== correctWord.id && !otherTestWords.some(function(o) { return o.id === w.id; });
      });
      var shuffledFallback = this.shuffleArray([...fallbackPool]);
      for (var k = 0; k < need && k < shuffledFallback.length; k++) {
        options.push(shuffledFallback[k].meaning);
      }
      // last resort: generic English definitions
      var genericDefs = ['to perform an action', 'relating to a state', 'a kind of place', 'an abstract concept'];
      var gi = 0;
      while (options.length < 4) {
        options.push(genericDefs[gi++] || 'unknown meaning');
      }
    }
    return this.shuffleArray(options);
  },

  selectTestOption: function(e) {
    const index = e.currentTarget.dataset.index;
    const selectedMeaning = this.data.testOptions[index];
    const isCorrect = selectedMeaning === this.data.currentWord.meaning;
    this.setData({
      selectedOption: index,
      showTestResult: true,
      isTestCorrect: isCorrect,
      testedCount: this.data.testedCount + 1
    });
    const actionType = isCorrect ? 'test_correct' : 'test_wrong';
    this.submitLearningRecord(this.data.currentWord.id, actionType);
  },

  // Bug4 fixed: use setData for testWords array instead of direct mutation
  nextTestWord: function() {
    const testWordIndex = this.data.testWordIndex;
    const isTestCorrect = this.data.isTestCorrect;
    const testWords = [...this.data.testWords]; // copy, not reference
    const currentTestWord = testWords[testWordIndex];
    const wordId = currentTestWord.id;

    const retryCount = this.data.testRetryCount;
    if (!retryCount[wordId]) retryCount[wordId] = 0;

    if (!isTestCorrect) {
      retryCount[wordId]++;
      if (retryCount[wordId] >= this.data.maxRetries) {
        wx.showToast({ title: '已重试3次，继续下一个', icon: 'none', duration: 1500 });
        testWords.splice(testWordIndex, 1);
      } else {
        testWords.splice(testWordIndex, 1);
        testWords.push(currentTestWord);
      }
    } else {
      testWords.splice(testWordIndex, 1);
    }
    // use setData to properly update array
    this.setData({ testWords: testWords, testRetryCount: retryCount });
    this.loadTestWord(testWordIndex);
  },

  submitLearningRecord: function(wordId, actionType) {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) return;

    const currentWord = this.data.currentWord && this.data.currentWord.id === wordId
      ? this.data.currentWord
      : (this.data.allWords || []).find(w => w.id === wordId) || null;

    const isCorrect = actionType === 'know' || actionType === 'test_correct';
    const bktProbability = this.getMockBktProbability(currentWord, isCorrect);

    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/record-custom',
      method: 'POST',
      header: {
        'Authorization': 'Bearer ' + app.globalData.sessionToken,
        'content-type': 'application/json'
      },
      data: {
        userId: userInfo.id,
        wordId: wordId,
        actionType: actionType,
        bktProbability: bktProbability
      },
      success: function(res) { console.log('record:', actionType, res.data.status); },
      fail: function(err) { console.error('record fail:', err); }
    });
  },

  getMockBktProbability: function(word, isCorrect) {
    const difficulty = (word && word.difficulty) ? word.difficulty : 'medium';
    const baseMap = { easy: 0.85, medium: 0.70, hard: 0.55 };
    let p = baseMap[difficulty] || 0.70;
    if (isCorrect === true) p = Math.min(p + 0.10, 0.95);
    if (isCorrect === false) p = Math.max(p - 0.25, 0.10);
    return Number(p.toFixed(2));
  },

  updateProgress: function() {
    const currentWordIndex = this.data.currentWordIndex;
    const totalWords = this.data.totalWords;
    const progress = totalWords > 0 ? Math.round((currentWordIndex + 1) / totalWords * 100) : 0;
    this.setData({ progress: progress });
  },

  showCompletion: function() {
    // 收集本次学习的所有单词，传给拼写测试
    const allWords = this.data.allWords;
    if (allWords && allWords.length > 0) {
      app.globalData.spellingTestWords = allWords.map(w => ({
        id: w.id,
        word: w.word,
        meaning: w.meaning,
        pronunciation: w.pronunciation || ''
      }));
      this.setData({ stage: 3, progress: 100 });
    } else {
      this.setData({ stage: 3, progress: 100 });
    }
  },

  goBackToStudy: function() {
    wx.navigateBack();
  },

  goToSpellingTest: function() {
    wx.navigateTo({ url: '/pages/spelling-test/spelling-test?source=learn' });
  },

  goBackToHome: function() {
    wx.switchTab({ url: '/pages/home/home' });
  },

  shuffleArray: function(array) {
    for (var i = array.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = array[i]; array[i] = array[j]; array[j] = tmp;
    }
    return array;
  }
});
