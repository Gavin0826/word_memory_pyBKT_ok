// pages/admin-edit-word/admin-edit-word.js
const app = getApp();

Page({
  data: {
    category: '',
    categoryName: '',
    words: [],
    total: 0,
    page: 1,
    pageSize: 20,
    totalPages: 1,
    keyword: '',
    loading: false,
    showEditModal: false,
    editForm: { id: null, word: '', pronunciation: '', meaning: '', difficulty: 'medium' },
    searchTimer: null
  },

  onLoad: function(options) {
    this.setData({
      category: options.category || '',
      categoryName: decodeURIComponent(options.name || '单词列表')
    });
    this.loadWords();
  },

  onShow: function() {
    const isAdmin = app.globalData.isAdmin || wx.getStorageSync('isAdmin');
    if (!isAdmin) { wx.reLaunch({ url: '/pages/login/login' }); return; }
  },

  loadWords: function() {
    this.setData({ loading: true });
    const { category, keyword, page, pageSize } = this.data;
    wx.request({
      url: app.globalData.apiBaseUrl + '/api/words/admin/list',
      method: 'GET',
      data: { category, keyword, page, pageSize },
      success: (res) => {
        this.setData({ loading: false });
        if (res.data && res.data.status === 'success') {
          this.setData({
            words: res.data.words || [],
            total: res.data.total || 0,
            totalPages: res.data.totalPages || 1
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

  onSearch: function(e) {
    clearTimeout(this.data.searchTimer);
    const timer = setTimeout(() => {
      this.setData({ keyword: e.detail.value, page: 1 }, () => this.loadWords());
    }, 500);
    this.setData({ searchTimer: timer, keyword: e.detail.value });
  },

  prevPage: function() {
    if (this.data.page <= 1) return;
    this.setData({ page: this.data.page - 1 }, () => this.loadWords());
  },

  nextPage: function() {
    if (this.data.page >= this.data.totalPages) return;
    this.setData({ page: this.data.page + 1 }, () => this.loadWords());
  },

  goToAddWord: function() {
    wx.navigateTo({ url: '/pages/admin-add-word/admin-add-word?category=' + this.data.category });
  },

  // ===== 编辑 =====
  editWord: function(e) {
    const word = e.currentTarget.dataset.word;
    this.setData({
      showEditModal: true,
      editForm: {
        id: word.id,
        word: word.word,
        pronunciation: word.pronunciation || '',
        meaning: word.meaning || '',
        difficulty: word.difficulty || 'medium'
      }
    });
  },

  onEditInput: function(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['editForm.' + field]: e.detail.value });
  },

  setEditDifficulty: function(e) {
    this.setData({ 'editForm.difficulty': e.currentTarget.dataset.val });
  },

  closeModal: function() {
    this.setData({ showEditModal: false });
  },

  saveEdit: function() {
    const { id, word, meaning } = this.data.editForm;
    if (!word.trim()) { wx.showToast({ title: '单词不能为空', icon: 'none' }); return; }
    if (!meaning.trim()) { wx.showToast({ title: '释义不能为空', icon: 'none' }); return; }

    wx.request({
      url: app.globalData.apiBaseUrl + '/api/words/admin/update/' + id,
      method: 'PUT',
      header: { 'content-type': 'application/json' },
      data: this.data.editForm,
      success: (res) => {
        if (res.data && res.data.status === 'success') {
          wx.showToast({ title: '修改成功', icon: 'success' });
          this.setData({ showEditModal: false });
          this.loadWords();
        } else {
          wx.showToast({ title: res.data.message || '修改失败', icon: 'none' });
        }
      },
      fail: () => wx.showToast({ title: '网络错误', icon: 'none' })
    });
  },

  // ===== 删除 =====
  deleteWord: function(e) {
    const { id, word } = e.currentTarget.dataset;
    wx.showModal({
      title: '确认删除',
      content: '确定删除单词「' + word + '」吗？相关学习记录也将被清除。',
      confirmColor: '#F44336',
      success: (res) => {
        if (res.confirm) {
          wx.request({
            url: app.globalData.apiBaseUrl + '/api/words/admin/delete/' + id,
            method: 'DELETE',
            success: (r) => {
              if (r.data && r.data.status === 'success') {
                wx.showToast({ title: '删除成功', icon: 'success' });
                this.loadWords();
              } else {
                wx.showToast({ title: r.data.message || '删除失败', icon: 'none' });
              }
            },
            fail: () => wx.showToast({ title: '网络错误', icon: 'none' })
          });
        }
      }
    });
  },

  goBack: function() { wx.navigateBack(); }
});
