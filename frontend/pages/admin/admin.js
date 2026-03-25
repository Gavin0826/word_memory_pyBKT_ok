// pages/admin/admin.js
const app = getApp();

Page({
  data: { adminInfo: null },

  onLoad: function() {},

  onShow: function() {
    const isAdmin = app.globalData.isAdmin || wx.getStorageSync('isAdmin');
    if (!isAdmin) { wx.reLaunch({ url: '/pages/login/login' }); return; }
    this.setData({ adminInfo: app.globalData.adminInfo || { username: '管理员' } });
  },

  // ========== 词库管理 ==========
  goToWordlistManage: function() {
    wx.navigateTo({ url: '/pages/admin-wordlist/admin-wordlist' });
  },
  goToAddWord: function() {
    wx.navigateTo({ url: '/pages/admin-add-word/admin-add-word' });
  },
  goToEditWord: function() {
    wx.navigateTo({ url: '/pages/admin-edit-word/admin-edit-word' });
  },
  goToImportWord: function() {
    wx.showModal({
      title: '批量导入',
      content: '批量导入功能正在开发中，敬请期待！',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  // ========== 用户管理 ==========
  goToUserList: function() {
    wx.navigateTo({ url: '/pages/admin-user-list/admin-user-list' });
  },
  goToUserStats: function() {
    wx.navigateTo({ url: '/pages/admin-user-list/admin-user-list' });
  },

  // ========== 系统设置 ==========
  goToSystemSettings: function() {
    wx.showModal({ title: '系统参数', content: '功能开发中，敬请期待！', showCancel: false, confirmText: '知道了' });
  },
  goToBackup: function() {
    wx.showModal({ title: '数据备份', content: '功能开发中，敬请期待！', showCancel: false, confirmText: '知道了' });
  },

  confirmLogout: function() {
    wx.showModal({
      title: '退出管理后台',
      content: '确定要退出管理员模式吗？',
      success: (res) => { if (res.confirm) this.logout(); }
    });
  },

  logout: function() {
    app.globalData.isAdmin = false;
    app.globalData.adminInfo = null;
    wx.removeStorageSync('isAdmin');
    wx.showToast({
      title: '已退出管理后台',
      icon: 'success',
      duration: 1500,
      success: () => { setTimeout(() => { wx.reLaunch({ url: '/pages/login/login' }); }, 1500); }
    });
  }
});
