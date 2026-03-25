// pages/study-settings/study-settings.js
const app = getApp();

Page({
  data: {
    newWordsPerDay: 10,
    reviewWordsPerSession: 10,
    sliderNewWords: 10,
    sliderReviewWords: 10
  },

  onLoad: function() {
    this.loadSettings();
  },

  loadSettings: function() {
    const settings = wx.getStorageSync('studySettings') || {};
    const newWordsPerDay = settings.newWordsPerDay || 10;
    const reviewWordsPerSession = settings.reviewWordsPerSession || 10;
    this.setData({
      newWordsPerDay,
      reviewWordsPerSession,
      sliderNewWords: newWordsPerDay,
      sliderReviewWords: reviewWordsPerSession
    });
    // 同步到全局
    app.globalData.studySettings = {
      newWordsPerDay,
      reviewWordsPerSession
    };
  },

  onNewWordsChange: function(e) {
    this.setData({ sliderNewWords: e.detail.value });
  },

  onReviewWordsChange: function(e) {
    this.setData({ sliderReviewWords: e.detail.value });
  },

  saveSettings: function() {
    const { sliderNewWords, sliderReviewWords } = this.data;
    const settings = {
      newWordsPerDay: sliderNewWords,
      reviewWordsPerSession: sliderReviewWords
    };
    wx.setStorageSync('studySettings', settings);
    app.globalData.studySettings = settings;

    wx.showToast({
      title: '设置已保存',
      icon: 'success',
      duration: 1500,
      success: () => {
        setTimeout(() => wx.navigateBack(), 1500);
      }
    });
  },

  resetSettings: function() {
    wx.showModal({
      title: '重置设置',
      content: '确定要恢复默认设置吗？',
      success: (res) => {
        if (res.confirm) {
          this.setData({
            sliderNewWords: 10,
            sliderReviewWords: 10
          });
          wx.showToast({ title: '已重置为默认值', icon: 'none' });
        }
      }
    });
  }
});
