// pages/wrong-words/wrong-words.js
const app = getApp();

Page({
  data: {
    userInfo: null,
    wrongWords: [],
    loading: false,
    sortBy: 'count',
    filterDifficulty: 'all',
    totalWrongCount: 0,
    uniqueWrongCount: 0,
    searchText: '',
    filteredWords: []
  },

  onLoad: function() {},

  onShow: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.setData({ userInfo: userInfo });
    this.loadWrongWords();
  },

  loadWrongWords: function() {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    this.setData({ loading: true });
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/' + userInfo.id + '/wrong-words',
      method: 'GET',
      header: {
        'Authorization': 'Bearer ' + app.globalData.sessionToken,
        'content-type': 'application/json'
      },
      success: (res) => {
        if (Array.isArray(res.data)) {
          const processedWords = this.processWrongWords(res.data);
          this.setData({
            wrongWords: processedWords,
            filteredWords: processedWords,
            totalWrongCount: res.data.length,
            uniqueWrongCount: processedWords.length,
            loading: false
          });
        } else {
          this.setData({ loading: false });
          wx.showToast({ title: '加载失败', icon: 'none' });
        }
      },
      fail: () => {
        this.setData({ loading: false });
        wx.showToast({ title: '网络错误', icon: 'none' });
      }
    });
  },

  processWrongWords: function(records) {
    const wordMap = {};
    records.forEach(record => {
      const word = record.word || {};
      const wordId = word.id || record.wordId;
      if (!wordMap[wordId]) {
        wordMap[wordId] = {
          id: wordId,
          word: word.word || '未知单词',
          pronunciation: word.pronunciation || '',
          meaning: word.meaning || '未知释义',
          difficulty: word.difficulty || 'medium',
          wrongCount: 0,
          lastWrongTime: null,
          records: []
        };
      }
      wordMap[wordId].wrongCount++;
      wordMap[wordId].records.push(record);
      const studyTime = new Date(record.studyTime);
      if (!wordMap[wordId].lastWrongTime || studyTime > new Date(wordMap[wordId].lastWrongTime)) {
        wordMap[wordId].lastWrongTime = record.studyTime;
      }
    });
    let result = Object.values(wordMap);
    result.sort((a, b) => b.wrongCount - a.wrongCount);
    return result;
  },

  changeSortBy: function(e) {
    const sortBy = e.currentTarget.dataset.sort;
    let words = [...this.data.wrongWords];
    if (sortBy === 'count') {
      words.sort((a, b) => b.wrongCount - a.wrongCount);
    } else if (sortBy === 'time') {
      words.sort((a, b) => new Date(b.lastWrongTime) - new Date(a.lastWrongTime));
    }
    this.setData({ sortBy: sortBy, filteredWords: this.applyFilters(words) });
  },

  changeDifficultyFilter: function(e) {
    this.setData({ filterDifficulty: e.currentTarget.dataset.difficulty }, () => {
      this.applyAllFilters();
    });
  },

  onSearchInput: function(e) {
    this.setData({ searchText: e.detail.value.toLowerCase() }, () => {
      this.applyAllFilters();
    });
  },

  clearSearch: function() {
    this.setData({ searchText: '' }, () => { this.applyAllFilters(); });
  },

  applyAllFilters: function() {
    let words = [...this.data.wrongWords];
    if (this.data.filterDifficulty !== 'all') {
      words = words.filter(w => w.difficulty === this.data.filterDifficulty);
    }
    if (this.data.searchText) {
      words = words.filter(w =>
        w.word.toLowerCase().includes(this.data.searchText) ||
        w.meaning.toLowerCase().includes(this.data.searchText)
      );
    }
    this.setData({ filteredWords: words });
  },

  applyFilters: function(words) {
    let result = [...words];
    if (this.data.filterDifficulty !== 'all') {
      result = result.filter(w => w.difficulty === this.data.filterDifficulty);
    }
    if (this.data.searchText) {
      result = result.filter(w =>
        w.word.toLowerCase().includes(this.data.searchText) ||
        w.meaning.toLowerCase().includes(this.data.searchText)
      );
    }
    return result;
  },

  getDifficultyColor: function(difficulty) {
    const colors = { easy: '#4CAF50', medium: '#FF9800', hard: '#F44336' };
    return colors[difficulty] || '#757575';
  },

  getDifficultyText: function(difficulty) {
    const texts = { easy: '简单', medium: '中等', hard: '困难' };
    return texts[difficulty] || '中等';
  },

  formatTime: function(timeString) {
    if (!timeString) return '未知';
    try {
      const date = new Date(timeString);
      const now = new Date();
      const diffMs = now - date;
      const diffMinutes = Math.floor(diffMs / (1000 * 60));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      if (diffMinutes < 1) return '刚刚';
      if (diffMinutes < 60) return diffMinutes + '分钟前';
      if (diffHours < 24) return diffHours + '小时前';
      if (diffDays < 7) return diffDays + '天前';
      return date.toLocaleDateString();
    } catch (e) { return '未知'; }
  },

  viewWordDetail: function(e) {
    const word = e.currentTarget.dataset.word;
    wx.showModal({
      title: word.word,
      content: '音标：' + word.pronunciation + '\n释义：' + word.meaning +
               '\n难度：' + this.getDifficultyText(word.difficulty) +
               '\n错误次数：' + word.wrongCount,
      showCancel: false,
      confirmText: '关闭'
    });
  },

  // Bug1 fixed: actually call backend to delete wrong records
  deleteWrongWord: function(e) {
    const index = e.currentTarget.dataset.index;
    const word = this.data.filteredWords[index];
    const userInfo = this.data.userInfo;
    wx.showModal({
      title: '删除确认',
      content: '确定要删除"' + word.word + '"吗？',
      success: (res) => {
        if (res.confirm) {
          wx.request({
            url: app.globalData.apiBaseUrl + '/api/study/' + userInfo.id + '/wrong-words/' + word.id,
            method: 'DELETE',
            header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
            success: () => {
              const wrongWords = this.data.wrongWords.filter(w => w.id !== word.id);
              this.setData({
                wrongWords: wrongWords,
                filteredWords: this.applyFilters(wrongWords),
                uniqueWrongCount: wrongWords.length
              });
              wx.showToast({ title: '已删除', icon: 'success' });
            },
            fail: () => { wx.showToast({ title: '删除失败', icon: 'none' }); }
          });
        }
      }
    });
  },

  // Bug1 fixed: actually call backend to clear all wrong records
  clearAllWrongWords: function() {
    const userInfo = this.data.userInfo;
    wx.showModal({
      title: '清空确认',
      content: '确定要清空所有错词吗？此操作不可撤销。',
      success: (res) => {
        if (res.confirm) {
          wx.request({
            url: app.globalData.apiBaseUrl + '/api/study/' + userInfo.id + '/wrong-words',
            method: 'DELETE',
            header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
            success: () => {
              this.setData({
                wrongWords: [],
                filteredWords: [],
                totalWrongCount: 0,
                uniqueWrongCount: 0
              });
              wx.showToast({ title: '已清空', icon: 'success' });
            },
            fail: () => { wx.showToast({ title: '操作失败', icon: 'none' }); }
          });
        }
      }
    });
  },

  goBack: function() {
    wx.navigateBack();
  }
});
