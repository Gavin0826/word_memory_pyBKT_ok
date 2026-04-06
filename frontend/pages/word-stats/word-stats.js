// pages/word-stats/word-stats.js
const app = getApp();

Page({
  data: {
    loading: true,
    stats: null,
    // 顶部总览
    totalWords: 0,
    masteredWords: 0,
    totalRecords: 0,
    accuracy: 0,
    studiedDays: 0,
    consecutiveDays: 0,
    totalMinutes: 0,
    totalHours: '0分钟',
    // 词库统计
    categoryStats: [],
    // 每日统计
    dailyStats: [],
    // 柱状图最大值（用于计算柱子高度比例）
    dailyMaxWords: 1,
    // 掌握概率列表
    masteryList: [],
    averageMastery: 0,
    showMastery: false
  },

  onLoad: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.loadStats(userInfo.id);
    this.loadMastery(userInfo.id);
  },

  loadStats: function(userId) {
    this.setData({ loading: true });
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/' + userId + '/stats',
      method: 'GET',
      header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
      success: (res) => {
        this.setData({ loading: false });
        if (res.data && res.data.status === 'success') {
          const d = res.data;
          const totalMinutes = d.totalMinutes || 0;
          const totalHours = totalMinutes >= 60
            ? Math.floor(totalMinutes / 60) + '小时' + (totalMinutes % 60 > 0 ? totalMinutes % 60 + '分' : '')
            : totalMinutes + '分钟';

          const dailyStats = (d.dailyStats || []).map(day => ({
            ...day,
            learnedWords: day.learnedWords || 0,
            totalRecords: day.totalRecords || 0,
            accuracy: day.accuracy || 0,
            hasStudy: day.hasStudy || false
          }));
          const dailyMaxWords = Math.max(1, ...dailyStats.map(d => d.learnedWords));

          const dailyWithHeight = dailyStats.map(day => ({
            ...day,
            barHeight: dailyMaxWords > 0
              ? Math.max(4, Math.round(day.learnedWords / dailyMaxWords * 100))
              : 0
          }));

          this.setData({
            totalWords: d.totalWords || 0,
            masteredWords: d.masteredWords || 0,
            totalRecords: d.totalRecords || 0,
            accuracy: d.accuracy || 0,
            studiedDays: d.studiedDays || 0,
            consecutiveDays: d.consecutiveDays || 0,
            totalMinutes: totalMinutes,
            totalHours: totalHours,
            categoryStats: d.categoryStats || [],
            dailyStats: dailyWithHeight,
            dailyMaxWords: dailyMaxWords
          });
        } else {
          wx.showToast({ title: '加载失败', icon: 'none' });
        }
      },
      fail: () => {
        this.setData({ loading: false });
        wx.showToast({ title: '网络错误', icon: 'none' });
      }
    });
  },

  loadMastery: function(userId) {
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/' + userId + '/mastery',
      method: 'GET',
      header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          this.setData({
            masteryList: res.data.masteryList || [],
            averageMastery: Math.round((res.data.averageMastery || 0) * 100),
            showMastery: (res.data.masteryList || []).length > 0
          });
        }
      }
    });
  },

  onPullDownRefresh: function() {
    const userInfo = app.globalData.userInfo;
    if (userInfo) {
      this.loadStats(userInfo.id);
      this.loadMastery(userInfo.id);
    }
    wx.stopPullDownRefresh();
  }
});
