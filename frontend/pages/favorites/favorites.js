// pages/favorites/favorites.js
const app = getApp();

Page({
  data: {
    favorites: [],
    loading: false
  },

  onLoad: function() {},

  onShow: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    this.loadFavorites();
  },

  onPullDownRefresh: function() {
    this.loadFavorites().then(() => wx.stopPullDownRefresh());
  },

  loadFavorites: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) return Promise.resolve();
    this.setData({ loading: true });
    return new Promise((resolve) => {
      wx.request({
        url: app.globalData.apiBaseUrl + '/api/favorite/' + userInfo.id + '/list',
        method: 'GET',
        header: { 'Authorization': 'Bearer ' + app.globalData.sessionToken },
        success: (res) => {
          this.setData({ loading: false });
          if (res.data && res.data.status === 'success') {
            this.setData({ favorites: res.data.favorites || [] });
          } else {
            wx.showToast({ title: '加载失败', icon: 'none' });
          }
          resolve();
        },
        fail: () => {
          this.setData({ loading: false });
          wx.showToast({ title: '网络错误', icon: 'none' });
          resolve();
        }
      });
    });
  },

  removeFavorite: function(e) {
    const wordId = e.currentTarget.dataset.wordid;
    const userInfo = app.globalData.userInfo;
    if (!userInfo) return;
    wx.showModal({
      title: '取消收藏',
      content: '确定从收藏夹移除该单词？',
      success: (res) => {
        if (!res.confirm) return;
        wx.request({
          url: app.globalData.apiBaseUrl + '/api/favorite/remove',
          method: 'DELETE',
          header: {
            'Authorization': 'Bearer ' + app.globalData.sessionToken,
            'content-type': 'application/json'
          },
          data: { userId: userInfo.id, wordId: wordId },
          success: (res) => {
            if (res.data && res.data.status === 'success') {
              const newList = this.data.favorites.filter(f => f.wordId !== wordId);
              this.setData({ favorites: newList });
              wx.showToast({ title: '已取消收藏', icon: 'success' });
            }
          },
          fail: () => wx.showToast({ title: '操作失败', icon: 'none' })
        });
      }
    });
  },

  goToStudy: function() {
    wx.switchTab({ url: '/pages/study/study' });
  }
});
