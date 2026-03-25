// pages/admin-user-list/admin-user-list.js
const app = getApp();

Page({
  data: { users: [], loading: false },

  onLoad: function() {},

  onShow: function() {
    const isAdmin = app.globalData.isAdmin || wx.getStorageSync('isAdmin');
    if (!isAdmin) { wx.reLaunch({ url: '/pages/login/login' }); return; }
    this.loadUsers();
  },

  loadUsers: function() {
    this.setData({ loading: true });
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/user/all',
      method: 'GET',
      success: (res) => {
        this.setData({ loading: false });
        if (res.data && res.data.status === 'success') {
          this.setData({ users: res.data.users || [] });
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

  goToStats: function(e) {
    const user = e.currentTarget.dataset.user;
    wx.navigateTo({
      url: '/pages/admin-user-stats/admin-user-stats?userId=' + user.id + '&username=' + encodeURIComponent(user.username)
    });
  },

  goBack: function() { wx.navigateBack(); }
});
