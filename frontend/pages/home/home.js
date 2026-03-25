// pages/home/home.js
const app = getApp();

Page({
  data: {
    userInfo: null,
    todayTask: null,
    reviewCount: 0,
    loadingReviewCount: false
  },

  onLoad: function(options) {
    console.log('首页加载');
  },

  onShow: function() {
    const userInfo = app.globalData.userInfo;
    
    if (!userInfo) {
      wx.reLaunch({
        url: '/pages/login/login'
      });
      return;
    }
    
    this.setData({ userInfo });
    this.loadTodayTask();
    this.loadReviewCount();
  },

  // 加载今日任务（仅获取新词数量）
  loadTodayTask: function() {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    // Bug6 fixed: pass last selected category, default to CET-4
    const lastCategory = wx.getStorageSync('lastCategory') || 'CET-4';
    app.getTodayTask(userInfo.id, lastCategory).then(task => {
      this.setData({ todayTask: task });
    }).catch(err => {
      console.error('加载任务失败:', err);
      // Bug3 fixed: show user-facing error instead of silent fail
      wx.showToast({ title: '任务加载失败，请检查网络', icon: 'none' });
    });
  },

  // 加载复习数量 - 与复习页面使用同一接口，确保数据同步
  loadReviewCount: function() {
    const userInfo = this.data.userInfo;
    if (!userInfo) return;
    
    this.setData({ loadingReviewCount: true });
    
    app.getReviewCount(userInfo.id).then(count => {
      this.setData({
        reviewCount: count,
        loadingReviewCount: false
      });
    }).catch(err => {
      console.error('加载复习数量失败:', err);
      this.setData({
        reviewCount: 0,
        loadingReviewCount: false
      });
    });
  },

  goToStudy: function() {
    wx.switchTab({
      url: '/pages/study/study'
    });
  },

  goToReview: function() {
    wx.switchTab({
      url: '/pages/review/review'
    });
  },

  goToLogin: function() {
    wx.reLaunch({
      url: '/pages/login/login'
    });
  }
});
