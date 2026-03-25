// pages/admin-wordlist/admin-wordlist.js
const app = getApp();

Page({
  data: {
    categories: [],
    totalWords: 0,
    loading: false
  },

  onLoad: function() {},

  onShow: function() {
    const isAdmin = app.globalData.isAdmin || wx.getStorageSync('isAdmin');
    if (!isAdmin) { wx.reLaunch({ url: '/pages/login/login' }); return; }
    this.loadCategories();
  },

  loadCategories: function() {
    this.setData({ loading: true });
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/words/categories',
      method: 'GET',
      success: (res) => {
        this.setData({ loading: false });
        if (res.data && res.data.status === 'success') {
          this.setData({
            categories: res.data.categories || [],
            totalWords: res.data.totalWords || 0
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

  goToWordList: function(e) {
    const category = e.currentTarget.dataset.category;
    const name = e.currentTarget.dataset.name;
    wx.navigateTo({ url: '/pages/admin-edit-word/admin-edit-word?category=' + category + '&name=' + encodeURIComponent(name) });
  },

  goToAddWord: function() {
    wx.navigateTo({ url: '/pages/admin-add-word/admin-add-word' });
  },

  goBack: function() { wx.navigateBack(); }
});
