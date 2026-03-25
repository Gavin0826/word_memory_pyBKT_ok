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

    // 并发请求用户信息和学习记录
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

    // 2. 学习记录
    wx.request({
      url: baseUrl + '/api/study/' + userId + '/review-words',
      method: 'GET',
      success: (res) => {
        if (Array.isArray(res.data)) {
          const records = res.data;
          const total = records.length;
          const correct = records.filter(r => r.isCorrect).length;
          const rate = total > 0 ? Math.round(correct / total * 100) : 0;

          // 按词库分类统计已学单词
          const cet4 = new Set();
          const cet6 = new Set();
          records.forEach(r => {
            if (r.word) {
              if (r.word.category === 'CET-4') cet4.add(r.word.id);
              if (r.word.category === 'CET-6') cet6.add(r.word.id);
            }
          });
          this.setData({
            studyRecords: records.slice(0, 20),
            totalRecords: total,
            correctRate: rate,
            cet4Learned: cet4.size,
            cet6Learned: cet6.size
          });
        }
        finish();
      },
      fail: finish
    });
  },

  goBack: function() { wx.navigateBack(); }
});
