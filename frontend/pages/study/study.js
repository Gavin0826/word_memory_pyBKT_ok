// pages/study/study.js
const app = getApp();

Page({
  data: {
    userInfo: null,
    categories: [
      { id: 'CET-4', name: 'CET-4', desc: '大学英语四级词汇', count: 0 },
      { id: 'CET-6', name: 'CET-6', desc: '大学英语六级词汇', count: 0 },
      { id: '考研', name: '考研词汇', desc: '研究生入学考试词汇', count: 0 }
    ],
    loading: false,
    favoritesCount: 0
  },

  onLoad: function() {},

  onShow: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.setData({ userInfo: userInfo });
    this.loadCategoryStats();
    this.loadFavoritesCount();
  },

  loadFavoritesCount: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) return;
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/favorite/' + userInfo.id + '/ids',
      method: 'GET',
      header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          this.setData({ favoritesCount: (res.data.favoriteWordIds || []).length });
        }
      }
    });
  },

  goToFavorites: function() {
    wx.navigateTo({ url: '/pages/favorites/favorites' });
  },

  loadCategoryStats: function() {
    const that = this;
    const categories = this.data.categories;
    categories.forEach(function(category, index) {
      wx.request({
        url: app.globalData.apiBaseUrl + '/api/words/stats',
        method: 'GET',
        success: function(res) {
          var count = 0;
          if (res.data) {
            try {
              var stats = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
              count = stats[category.id] || 0;
            } catch (e) { count = 0; }
          }
          var key = 'categories[' + index + '].count';
          that.setData({ [key]: count });
        }
      });
    });
  },

  // Bug6 fix: save last selected category so home page loads correct task
  selectCategory: function(e) {
    const category = e.currentTarget.dataset.category;
    wx.setStorageSync('lastCategory', category);
    wx.navigateTo({
      url: '/pages/wordlearning/wordlearning?category=' + category
    });
  },

  goBack: function() {
    wx.switchTab({ url: '/pages/home/home' });
  }
});
