// app.js - 应用逻辑文件

App({
  // 全局数据
  globalData: {
    userInfo: null,
    sessionToken: null,
    apiBaseUrl: 'http://localhost:8080',
    studySettings: {
      newWordsPerDay: 10,
      reviewWordsPerSession: 10
    },
    isLoggingOut: false
  },

  // 小程序初始化
  onLaunch: function() {
    console.log('小程序初始化');

    // 读取用户保存的学习设置
    const savedSettings = wx.getStorageSync('studySettings');
    if (savedSettings && savedSettings.newWordsPerDay) {
      this.globalData.studySettings = {
        newWordsPerDay: savedSettings.newWordsPerDay || 10,
        reviewWordsPerSession: savedSettings.reviewWordsPerSession || 10
      };
    }
    
    // 检查网络状态
    this.checkNetwork();
    
    // 从本地存储读取用户信息和会话令牌
    const userInfo = wx.getStorageSync('userInfo');
    const sessionToken = wx.getStorageSync('sessionToken');
    
    if (userInfo && sessionToken) {
      this.globalData.userInfo = userInfo;
      this.globalData.sessionToken = sessionToken;
      
      // 异步验证会话有效性（不阻塞启动）
      this.validateSession(sessionToken);
    }
    // 注意：不在 onLaunch 中做页面跳转，由各页面的 onShow 自行判断登录状态
  },

  // 检查网络状态
  checkNetwork: function() {
    wx.getNetworkType({
      success: (res) => {
        const networkType = res.networkType;
        if (networkType === 'none') {
          wx.showToast({
            title: '网络连接失败，请检查网络设置',
            icon: 'none'
          });
        }
      }
    });
  },

  // 验证会话有效性
  validateSession: function(sessionToken) {
    const that = this;
    
    wx.request({
      url: this.globalData.apiBaseUrl + '/api/user/validate-session',
      method: 'POST',
      header: {
        'Authorization': 'Bearer ' + sessionToken,
        'content-type': 'application/json'
      },
      success: function(res) {
        if (res.data.status === 'success') {
          // 更新用户信息
          that.globalData.userInfo = res.data.user;
          wx.setStorageSync('userInfo', res.data.user);
          console.log('会话验证成功:', res.data.user.username);
        } else {
          // 会话无效，清除本地存储
          that.clearLoginState();
        }
      },
      fail: function(err) {
        console.error('会话验证失败:', err);
        wx.showToast({
          title: '网络连接异常',
          icon: 'none',
          duration: 2000
        });
      }
    });
  },

  // 通用请求方法（带认证）
  request: function(options) {
    const sessionToken = this.globalData.sessionToken;
    
    // 添加认证头
    if (!options.header) {
      options.header = {};
    }
    
    if (sessionToken) {
      options.header['Authorization'] = 'Bearer ' + sessionToken;
    }
    
    return new Promise((resolve, reject) => {
      wx.request({
        ...options,
        success: (res) => {
          if (res.data.status === 'success') {
            resolve(res.data);
          } else {
            // 处理认证失败
            if (res.data.message &&
               (res.data.message.includes('会话') ||
                res.data.message.includes('登录') ||
                res.data.message.includes('认证'))) {
              this.clearLoginState();
              wx.redirectTo({ url: '/pages/login/login' });
            }
            reject(res.data);
          }
        },
        fail: (err) => {
          reject(err);
        }
      });
    });
  },

  // Bug6 fixed: getTodayTask no longer hardcodes CET-4; category comes from caller
  getTodayTask: function(userId, category) {
    return new Promise((resolve, reject) => {
      const sessionToken = this.globalData.sessionToken;
      if (!sessionToken) { reject('用户未登录'); return; }
      wx.request({
        url: this.globalData.apiBaseUrl + '/api/words/today-task',
        method: 'GET',
        header: {
          'Authorization': 'Bearer ' + sessionToken,
          'content-type': 'application/json'
        },
        data: {
          userId: userId,
          newWordsCount: this.globalData.studySettings.newWordsPerDay,
          category: category || 'CET-4'
        },
        success: (res) => {
          if (res.data.status === 'success') { resolve(res.data); }
          else { reject('获取任务失败: ' + res.data.message); }
        },
        fail: (err) => { reject('请求失败: ' + err.errMsg); }
      });
    });
  },

  // 获取需要复习的单词列表（通用方法）
  getReviewWords: function(userId) {
    return new Promise((resolve, reject) => {
      const sessionToken = this.globalData.sessionToken;
      
      if (!sessionToken) {
        reject('用户未登录');
        return;
      }
      
      wx.request({
        url: this.globalData.apiBaseUrl + '/api/study/' + userId + '/review-words',
        method: 'GET',
        header: {
          'Authorization': 'Bearer ' + sessionToken,
          'content-type': 'application/json'
        },
        success: (res) => {
          // 直接返回数组或对象中的数组
          if (Array.isArray(res.data)) {
            resolve(res.data);
          } else if (res.data && Array.isArray(res.data.reviewWords)) {
            resolve(res.data.reviewWords);
          } else if (res.data && res.data.status === 'error') {
            reject('获取复习单词失败: ' + res.data.message);
          } else {
            reject('数据格式错误');
          }
        },
        fail: (err) => {
          reject('请求失败: ' + err.errMsg);
        }
      });
    });
  },

  // 获取复习单词数量（首页专用）
  getReviewCount: function(userId) {
    return new Promise((resolve, reject) => {
      this.getReviewWords(userId)
        .then(words => {
          resolve(words.length);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  // 提交学习记录
  submitStudyRecord: function(recordData) {
    return new Promise((resolve, reject) => {
      const sessionToken = this.globalData.sessionToken;
      
      if (!sessionToken) {
        reject('用户未登录');
        return;
      }
      
      wx.request({
        url: this.globalData.apiBaseUrl + '/api/study/record',
        method: 'POST',
        header: {
          'Authorization': 'Bearer ' + sessionToken,
          'content-type': 'application/json'
        },
        data: recordData,
        success: (res) => {
          if (res.data.status === 'success') {
            resolve(res.data);
          } else {
            reject('提交失败: ' + res.data.message);
          }
        },
        fail: (err) => {
          reject('请求失败: ' + err.errMsg);
        }
      });
    });
  },

  // 获取用户信息
  getUserInfo: function() {
    return this.globalData.userInfo;
  },

  // 更新用户信息
  updateUserInfo: function(userInfo) {
    this.globalData.userInfo = userInfo;
    wx.setStorageSync('userInfo', userInfo);
  },

  // 清除登录状态（不跳转）
  clearLoginState: function() {
    this.globalData.userInfo = null;
    this.globalData.sessionToken = null;
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('sessionToken');
    wx.removeStorageSync('lastUsername');
  },

  // 执行前端退出逻辑
  performLogout: function() {
    // 防止重复执行
    if (this.globalData.isLoggingOut) return;
    this.globalData.isLoggingOut = true;
    
    // 清除全局数据
    this.globalData.userInfo = null;
    this.globalData.sessionToken = null;
    
    // 清除本地存储
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('sessionToken');
    wx.removeStorageSync('lastUsername');
    
    console.log('已清除登录状态');
    
    // 跳转到登录页面
    wx.reLaunch({
      url: '/pages/login/login',
      success: () => {
        console.log('已跳转到登录页面');
        setTimeout(() => {
          this.globalData.isLoggingOut = false;
        }, 1000);
      },
      fail: (err) => {
        console.error('跳转失败:', err);
        this.globalData.isLoggingOut = false;
      }
    });
  },

  // 退出登录
  logout: function() {
    const sessionToken = this.globalData.sessionToken;
    
    // 先调用后端退出接口
    if (sessionToken) {
      wx.request({
        url: this.globalData.apiBaseUrl + '/api/user/logout',
        method: 'POST',
        header: {
          'Authorization': 'Bearer ' + sessionToken,
          'content-type': 'application/json'
        },
        success: (res) => {
          console.log('退出登录请求成功:', res.data);
        },
        fail: (err) => {
          console.error('退出登录请求失败:', err);
        },
        complete: () => {
          // 无论后端请求成功与否，都执行前端退出
          this.performLogout();
        }
      });
    } else {
      // 如果没有sessionToken，直接执行前端退出
      this.performLogout();
    }
  },

  // 显示加载提示
  showLoading: function(title = '加载中...') {
    wx.showLoading({
      title: title,
      mask: true
    });
  },

  // 隐藏加载提示
  hideLoading: function() {
    wx.hideLoading();
  },

  // 显示消息提示
  showToast: function(title, icon = 'none', duration = 2000) {
    wx.showToast({
      title: title,
      icon: icon,
      duration: duration
    });
  },

  // 显示模态对话框
  showModal: function(title, content, showCancel = true) {
    return new Promise((resolve, reject) => {
      wx.showModal({
        title: title,
        content: content,
        showCancel: showCancel,
        success: (res) => {
          if (res.confirm) {
            resolve(true);
          } else if (res.cancel) {
            resolve(false);
          }
        },
        fail: (err) => {
          reject(err);
        }
      });
    });
  }
});