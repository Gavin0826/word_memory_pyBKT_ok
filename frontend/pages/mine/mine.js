// pages/mine/mine.js
const app = getApp();

Page({
  data: {
    userInfo: null,
    stats: {
      totalWords: 0,
      studiedDays: 0,
      accuracy: 0,
      totalTime: '0分钟',
      consecutiveDays: 0
    },
    wrongWordsCount: 0,
    hasUpdate: false,
    loading: false,
    isLoginPage: false
  },

  onLoad: function(options) {},

  onShow: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      this.setData({
        isLoginPage: true,
        userInfo: null,
        stats: { totalWords: 0, studiedDays: 0, accuracy: 0, totalTime: '0分钟', consecutiveDays: 0 },
        wrongWordsCount: 0
      });
      return;
    }
    this.setData({ userInfo: userInfo, avatarText: (userInfo.username || '?').charAt(0).toUpperCase(), isLoginPage: false });
    this.loadUserData();
  },

  onHide: function() {
    this.setData({ loading: false });
  },

  loadUserData: function() {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    this.loadUserStats(userInfo.id);
    this.loadWrongWordsCount(userInfo.id);
  },

  // 从真实统计接口加载数据
  loadUserStats: function(userId) {
    this.setData({ loading: true });
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/' + userId + '/stats',
      method: 'GET',
      header: {
        'Authorization': 'Bearer ' + app.globalData.sessionToken,
        'content-type': 'application/json'
      },
      success: (res) => {
        this.setData({ loading: false });
        if (res.data && res.data.status === 'success') {
          const d = res.data;
          const totalMinutes = d.totalMinutes || 0;
          const totalTime = totalMinutes >= 60
            ? Math.floor(totalMinutes / 60) + '小时'
            : totalMinutes + '分钟';
          this.setData({
            stats: {
              totalWords: d.totalWords || 0,
              studiedDays: d.studiedDays || 0,
              consecutiveDays: d.consecutiveDays || 0,
              accuracy: d.accuracy || 0,
              totalTime: totalTime
            }
          });
        }
      },
      fail: () => {
        this.setData({ loading: false });
      }
    });
  },

  loadWrongWordsCount: function(userId) {
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/study/' + userId + '/wrong-words',
      method: 'GET',
      header: {
        'Authorization': 'Bearer ' + app.globalData.sessionToken,
        'content-type': 'application/json'
      },
      success: (res) => {
        if (res.data) {
          this.setData({ wrongWordsCount: res.data.length || 0 });
        }
      }
    });
  },

  getAvatarText: function(username) {
    if (!username) return '用';
    return username.length >= 2 ? username.substring(0, 2) : username;
  },

  goToStudySettings: function() {
    wx.navigateTo({ url: '/pages/study-settings/study-settings' });
  },

  goToWrongWords: function() {
    if (!this.checkLogin()) return;
    wx.navigateTo({ url: '/pages/wrong-words/wrong-words' });
  },

  goToWordStats: function() {
    if (!this.checkLogin()) return;
    wx.navigateTo({ url: '/pages/word-stats/word-stats' });
  },

  goToAbout: function() {
    wx.navigateTo({ url: '/pages/about/about' });
  },

  clearCache: function() {
    wx.showModal({
      title: '清理缓存',
      content: '确定要清理缓存数据吗？',
      success: (res) => {
        if (res.confirm) {
          wx.clearStorage();
          wx.showToast({ title: '缓存已清理', icon: 'success' });
        }
      }
    });
  },

  checkUpdate: function() {
    this.setData({ loading: true });
    setTimeout(() => {
      this.setData({ loading: false, hasUpdate: false });
      wx.showToast({ title: '已是最新版本', icon: 'none' });
    }, 1000);
  },

  logout: function() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '退出中...', mask: true });
          app.logout();
          setTimeout(() => { wx.hideLoading(); }, 1500);
        }
      }
    });
  },

  goToLogin: function() {
    wx.reLaunch({ url: '/pages/login/login' });
  },

  checkLogin: function() {
    if (!app.globalData.userInfo) {
      this.goToLogin();
      return false;
    }
    return true;
  },

  onUnload: function() {
    this.setData({ userInfo: null, loading: false });
  }
});
