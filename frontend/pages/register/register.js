// pages/register/register.js
const app = getApp();

Page({
  data: {
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    strengthText: '弱',
    strengthClass: 'weak',
    loading: false
  },

  onLoad: function(options) {
    // 页面加载时没有特殊操作
  },

  onUsernameInput: function(e) {
    this.setData({ username: e.detail.value });
  },

  onEmailInput: function(e) {
    this.setData({ email: e.detail.value });
  },

  onPasswordInput: function(e) {
    const password = e.detail.value;
    this.setData({ password: password });
    
    // 检查密码强度
    if (password.length === 0) {
      this.setData({
        strengthText: '弱',
        strengthClass: 'weak'
      });
    } else {
      this.checkPasswordStrength(password);
    }
  },

  onConfirmPasswordInput: function(e) {
    this.setData({ confirmPassword: e.detail.value });
  },

  // 检查密码强度
  checkPasswordStrength: function(password) {
    let score = 0;
    
    // 长度检查
    if (password.length >= 6) score += 1;
    if (password.length >= 8) score += 1;
    if (password.length >= 12) score += 1;
    
    // 包含数字
    if (/\d/.test(password)) score += 1;
    
    // 包含小写字母
    if (/[a-z]/.test(password)) score += 1;
    
    // 包含大写字母
    if (/[A-Z]/.test(password)) score += 1;
    
    // 包含特殊字符
    if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) score += 1;
    
    let strengthText = '弱';
    let strengthClass = 'weak';
    
    if (score >= 5) {
      strengthText = '强';
      strengthClass = 'strong';
    } else if (score >= 3) {
      strengthText = '中';
      strengthClass = 'medium';
    }
    
    this.setData({
      strengthText: strengthText,
      strengthClass: strengthClass
    });
  },

  register: function() {
    const that = this;
    const { username, email, password, confirmPassword } = this.data;

    // 验证用户名
    if (!username.trim()) {
      wx.showToast({
        title: '请输入用户名',
        icon: 'none'
      });
      return;
    }
    
    if (username.length < 4 || username.length > 20) {
      wx.showToast({
        title: '用户名长度应为4-20个字符',
        icon: 'none'
      });
      return;
    }
    
    // 验证邮箱格式（如果提供了邮箱）
    if (email && !email.includes('@')) {
      wx.showToast({
        title: '请输入有效的邮箱地址',
        icon: 'none'
      });
      return;
    }
    
    // 验证密码
    if (!password.trim()) {
      wx.showToast({
        title: '请输入密码',
        icon: 'none'
      });
      return;
    }
    
    if (password.length < 6) {
      wx.showToast({
        title: '密码长度至少6位',
        icon: 'none'
      });
      return;
    }
    
    if (password !== confirmPassword) {
      wx.showToast({
        title: '两次输入的密码不一致',
        icon: 'none'
      });
      return;
    }

    this.setData({ loading: true });

    wx.request({
      url: app.globalData.apiBaseUrl + '/api/user/register',
      method: 'POST',
      header: {
        'content-type': 'application/json'
      },
      data: {
        username: username.trim(),
        email: email.trim() || '',
        password: password.trim(),
        confirmPassword: confirmPassword.trim()
      },
      success: function(res) {
        that.setData({ loading: false });

        if (res.data.status === 'success') {
          wx.showToast({
            title: '注册成功',
            icon: 'success',
            duration: 2000,
            success: () => {
              setTimeout(() => {
                // 返回登录页面
                wx.navigateBack();
              }, 2000);
            }
          });
        } else {
          wx.showToast({
            title: res.data.message || '注册失败',
            icon: 'none'
          });
        }
      },
      fail: function(err) {
        that.setData({ loading: false });
        wx.showToast({
          title: '网络错误，请稍后重试',
          icon: 'none'
        });
        console.error('注册失败:', err);
      }
    });
  },

  goToLogin: function() {
    wx.navigateBack();
  }
});