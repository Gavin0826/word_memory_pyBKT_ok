// pages/login/login.js
const app = getApp();

// 管理员固定账号密码
const ADMIN_ID = '654321';
const ADMIN_PWD = '654321';

Page({
  data: {
    loginMode: 'user', // 'user' | 'admin'
    
    // 用户登录
    identifier: '',
    password: '',
    rememberMe: false,
    loading: false,
    isLoggingIn: false,

    // 管理员登录
    adminIdentifier: '',
    adminPassword: '',
    adminLoading: false
  },

  onLoad: function(options) {
    console.log('登录页面加载');
    const savedUsername = wx.getStorageSync('lastUsername');
    if (savedUsername) {
      this.setData({ identifier: savedUsername, rememberMe: true });
    }
    this.checkAutoLogin();
  },

  onShow: function() {
    if (app.globalData.userInfo && app.globalData.sessionToken) {
      this.redirectToHome();
    }
    this.setData({
      password: '',
      adminPassword: '',
      loading: false,
      adminLoading: false,
      isLoggingIn: false
    });
  },

  // 切换登录模式
  switchMode: function(e) {
    const mode = e.currentTarget.dataset.mode;
    this.setData({
      loginMode: mode,
      password: '',
      adminPassword: '',
      loading: false,
      adminLoading: false
    });
  },

  onIdentifierInput: function(e) {
    this.setData({ identifier: e.detail.value });
  },

  onPasswordInput: function(e) {
    this.setData({ password: e.detail.value });
  },

  onRememberChange: function(e) {
    this.setData({ rememberMe: e.detail.value });
  },

  onAdminIdentifierInput: function(e) {
    this.setData({ adminIdentifier: e.detail.value });
  },

  onAdminPasswordInput: function(e) {
    this.setData({ adminPassword: e.detail.value });
  },

  // 检查自动登录
  checkAutoLogin: function() {
    const sessionToken = wx.getStorageSync('sessionToken');
    if (sessionToken) {
      this.setData({ loading: true });
      wx.request({
        url: app.globalData.apiBaseUrl + '/api/user/validate-session',
        method: 'POST',
        header: {
          'Authorization': 'Bearer ' + sessionToken,
          'content-type': 'application/json'
        },
        success: (res) => {
          this.setData({ loading: false });
          if (res.data.status === 'success') {
            app.globalData.userInfo = res.data.user;
            app.globalData.sessionToken = sessionToken;
            this.redirectToHome();
          } else {
            wx.removeStorageSync('sessionToken');
            wx.removeStorageSync('userInfo');
            app.globalData.userInfo = null;
            app.globalData.sessionToken = null;
          }
        },
        fail: () => {
          this.setData({ loading: false });
          wx.removeStorageSync('sessionToken');
          app.globalData.userInfo = null;
          app.globalData.sessionToken = null;
        }
      });
    }
  },

  // 用户登录
  login: function() {
    if (this.data.isLoggingIn) return;
    const { identifier, password, rememberMe } = this.data;

    if (!identifier.trim()) {
      wx.showToast({ title: '请输入用户名或邮箱', icon: 'none' });
      return;
    }
    if (!password.trim()) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }

    this.setData({ loading: true, isLoggingIn: true });

    wx.request({
      url: app.globalData.apiBaseUrl + '/api/user/login',
      method: 'POST',
      header: { 'content-type': 'application/json' },
      data: {
        identifier: identifier.trim(),
        password: password.trim(),
        rememberMe: rememberMe
      },
      success: (res) => {
        this.setData({ loading: false, isLoggingIn: false });
        if (res.data.status === 'success') {
          app.globalData.userInfo = res.data.user;
          app.globalData.sessionToken = res.data.sessionToken;
          wx.setStorageSync('userInfo', res.data.user);
          wx.setStorageSync('sessionToken', res.data.sessionToken);
          if (rememberMe) {
            wx.setStorageSync('lastUsername', identifier.trim());
          } else {
            wx.removeStorageSync('lastUsername');
          }
          wx.showToast({
            title: '登录成功',
            icon: 'success',
            duration: 1500,
            success: () => {
              this.setData({ identifier: '', password: '', rememberMe: false });
              setTimeout(() => this.redirectToHome(), 1500);
            }
          });
        } else {
          wx.showToast({ title: res.data.message || '登录失败', icon: 'none' });
        }
      },
      fail: (err) => {
        this.setData({ loading: false, isLoggingIn: false });
        wx.showToast({ title: '网络错误，请稍后重试', icon: 'none' });
        console.error('登录失败:', err);
      }
    });
  },

  // 管理员登录
  adminLogin: function() {
    const { adminIdentifier, adminPassword } = this.data;

    if (!adminIdentifier.trim()) {
      wx.showToast({ title: '请输入管理员账号', icon: 'none' });
      return;
    }
    if (!adminPassword.trim()) {
      wx.showToast({ title: '请输入管理员密码', icon: 'none' });
      return;
    }

    this.setData({ adminLoading: true });

    // 本地验证管理员账号密码
    setTimeout(() => {
      if (adminIdentifier.trim() === ADMIN_ID && adminPassword.trim() === ADMIN_PWD) {
        // 验证成功，设置管理员全局状态
        app.globalData.isAdmin = true;
        app.globalData.adminInfo = {
          id: 'admin',
          username: '管理员',
          role: 'admin'
        };
        wx.setStorageSync('isAdmin', true);

        this.setData({ adminLoading: false, adminIdentifier: '', adminPassword: '' });

        wx.showToast({
          title: '管理员登录成功',
          icon: 'success',
          duration: 1500,
          success: () => {
            setTimeout(() => {
              wx.reLaunch({ url: '/pages/admin/admin' });
            }, 1500);
          }
        });
      } else {
        this.setData({ adminLoading: false });
        wx.showToast({ title: '账号或密码错误', icon: 'none' });
      }
    }, 600);
  },

  redirectToHome: function() {
    wx.reLaunch({
      url: '/pages/home/home',
      success: () => console.log('已跳转到首页'),
      fail: (err) => console.error('跳转失败:', err)
    });
  },

  goToRegister: function() {
    wx.navigateTo({ url: '/pages/register/register' });
  },

  onForgotPassword: function() {
    wx.showModal({
      title: '忘记密码',
      content: '请联系系统管理员或使用注册邮箱找回密码',
      showCancel: false
    });
  },

  onUnload: function() {
    this.setData({ loading: false, adminLoading: false, isLoggingIn: false });
  }
});
