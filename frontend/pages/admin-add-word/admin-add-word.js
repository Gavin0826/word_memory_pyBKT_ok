// pages/admin-add-word/admin-add-word.js
const app = getApp();

Page({
  data: {
    form: {
      word: '',
      pronunciation: '',
      meaning: '',
      category: 'CET-4',
      difficulty: 'medium'
    },
    loading: false
  },

  onLoad: function(options) {
    // 若从词库列表跳转，预填分类
    if (options.category) {
      this.setData({ 'form.category': options.category });
    }
  },

  onInput: function(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  setCategory: function(e) {
    this.setData({ 'form.category': e.currentTarget.dataset.val });
  },

  setDifficulty: function(e) {
    this.setData({ 'form.difficulty': e.currentTarget.dataset.val });
  },

  submitWord: function() {
    const { word, meaning, category } = this.data.form;
    if (!word.trim()) { wx.showToast({ title: '请输入单词', icon: 'none' }); return; }
    if (!meaning.trim()) { wx.showToast({ title: '请输入释义', icon: 'none' }); return; }
    if (!category) { wx.showToast({ title: '请选择词库', icon: 'none' }); return; }

    this.setData({ loading: true });
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/words/admin/add',
      method: 'POST',
      header: { 'content-type': 'application/json' },
      data: this.data.form,
      success: (res) => {
        this.setData({ loading: false });
        if (res.data && res.data.status === 'success') {
          wx.showToast({ title: '添加成功', icon: 'success' });
          // 重置表单
          setTimeout(() => {
            this.setData({
              form: { word: '', pronunciation: '', meaning: '', category: this.data.form.category, difficulty: 'medium' }
            });
          }, 1500);
        } else {
          wx.showToast({ title: res.data.message || '添加失败', icon: 'none' });
        }
      },
      fail: () => {
        this.setData({ loading: false });
        wx.showToast({ title: '网络错误', icon: 'none' });
      }
    });
  },

  goBack: function() { wx.navigateBack(); }
});
