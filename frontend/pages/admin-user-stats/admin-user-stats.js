// pages/admin-user-stats/admin-user-stats.js
const app = getApp();

Page({
  data: {
    userId: null,
    username: '',
    userInfo: null,
    studyRecords: [],
    cet4Learned: 0,
    cet6Learned: 0,
    totalRecords: 0,
    correctRate: 0,
    totalWords: 0,
    studiedDays: 0,
    loading: false
  },

  onLoad: function(options) {
    this.setData({
      userId: options.userId,
      username: decodeURIComponent(options.username || '用户')
    });
    this.loadStats();
  },

  onShow: function() {
    const isAdmin = app.globalData.isAdmin || wx.getStorageSync('isAdmin');
    if (!isAdmin) { wx.reLaunch({ url: '/pages/login/login' }); return; }
  },

  loadStats: function() {
    const { userId } = this.data;
    if (!userId) return;
    this.setData({ loading: true });

    const baseUrl = app.globalData.apiBaseUrl;
    let done = 0;
    const finish = () => { done++; if (done === 2) this.setData({ loading: false }); };

    // 1. 用户基本信息
    wx.request({
      url: baseUrl + '/api/user/admin/study-progress/' + userId,
      method: 'GET',
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          this.setData({ userInfo: res.data.user });
        }
        finish();
      },
      fail: finish
    });

    // 2. 学习统计（与用户端一致）
    wx.request({
      url: baseUrl + '/api/study/' + userId + '/stats',
      method: 'GET',
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          const d = res.data;
          const categoryStats = d.categoryStats || [];
          const cet4 = categoryStats.find(c => c.category === 'CET-4');
          const cet6 = categoryStats.find(c => c.category === 'CET-6');

          this.setData({
            studyRecords: [],
            totalRecords: d.totalRecords || 0,
            correctRate: d.accuracy || 0,
            totalWords: d.masteredWords || 0,
            studiedDays: d.studiedDays || 0,
            cet4Learned: cet4 ? (cet4.learnedWords || 0) : 0,
            cet6Learned: cet6 ? (cet6.learnedWords || 0) : 0
          });
        }
        finish();
      },
      fail: finish
    });
  },

  goBack: function() { wx.navigateBack(); }
});
