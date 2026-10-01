import type { BootstrapData, Message } from '../types'

function svgData(svg: string) {
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

function avatar(label: string, background: string) {
  return svgData(`<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><rect width="96" height="96" rx="28" fill="${background}"/><circle cx="48" cy="39" r="17" fill="#ffffff" opacity=".9"/><path d="M20 81c3-15 14-23 28-23s25 8 28 23" fill="#ffffff" opacity=".9"/><text x="48" y="91" text-anchor="middle" font-size="12" font-family="Arial" fill="#334155">${label}</text></svg>`)
}

function mockImage() {
  return svgData('<svg xmlns="http://www.w3.org/2000/svg" width="720" height="430" viewBox="0 0 720 430"><rect width="720" height="430" rx="22" fill="#eaf2ff"/><circle cx="575" cy="117" r="58" fill="#ffd98a"/><path d="M0 342L145 205l90 77 110-129 175 189H0z" fill="#9bc5ff"/><path d="M0 372l176-129 95 74 113-82 210 135H0z" fill="#6aa2f8"/><rect x="36" y="36" width="230" height="48" rx="24" fill="#fff" opacity=".9"/><text x="58" y="67" font-size="22" font-family="Arial" fill="#5279b7">mock image</text></svg>')
}

function mockVideoThumbnail() {
  return svgData('<svg xmlns="http://www.w3.org/2000/svg" width="720" height="405" viewBox="0 0 720 405"><defs><linearGradient id="g" x1="0" x2="1"><stop stop-color="#40556f"/><stop offset="1" stop-color="#182535"/></linearGradient></defs><rect width="720" height="405" rx="18" fill="url(#g)"/><circle cx="360" cy="202" r="58" fill="#000" opacity=".38"/><path d="M344 169l54 33-54 33z" fill="#fff"/><text x="28" y="371" font-size="22" font-family="Arial" fill="#fff" opacity=".82">周会演示.mp4</text></svg>')
}

const now = Date.now()
const minute = 60_000
const yesterday = new Date()
yesterday.setDate(yesterday.getDate() - 1)
yesterday.setHours(18, 20, 0, 0)
const previousYear = new Date(new Date().getFullYear() - 1, 11, 28, 10, 12).getTime()

const avatars = {
  current: avatar('周', '#b6e3f4'),
  lin: avatar('林', '#ffd5dc'),
  meng: avatar('孟', '#c0aede'),
  chen: avatar('陈', '#ffdfbf'),
  design: avatar('设', '#d1d4f9'),
  code: avatar('班', '#b6e3f4'),
}

const baseMessages: Record<string, Message[]> = {
  'c-lin': [
    { id: 'm-100', conversationId: 'c-lin', senderId: 'u-lin', senderName: '林晓', senderAvatar: avatars.lin, type: 'text', content: '去年的原型稿我也整理好了。', createdAt: previousYear },
    { id: 'm-101', conversationId: 'c-lin', senderId: 'u-lin', senderName: '林晓', senderAvatar: avatars.lin, type: 'text', content: '最近项目进度怎么样啦？', createdAt: yesterday.getTime() },
    { id: 'm-102', conversationId: 'c-lin', senderId: 'me', senderName: '我', senderAvatar: avatars.current, type: 'text', content: '页面原型已经完成，今天准备把交互再细化一下。', createdAt: yesterday.getTime() + 3 * minute },
    { id: 'm-103', conversationId: 'c-lin', senderId: 'u-lin', senderName: '林晓', senderAvatar: avatars.lin, type: 'file', fileKind: 'image', content: mockImage(), fileName: '清爽配色参考.png', fileSize: '386 KB', createdAt: yesterday.getTime() + 8 * minute },
    { id: 'm-104', conversationId: 'c-lin', senderId: 'u-lin', senderName: '林晓', senderAvatar: avatars.lin, type: 'text', content: '这个配色可以参考一下，感觉比较清爽。', createdAt: yesterday.getTime() + 9 * minute },
    { id: 'm-105', conversationId: 'c-lin', senderId: 'me', senderName: '我', senderAvatar: avatars.current, type: 'file', fileKind: 'document', content: 'data:text/plain;charset=UTF-8,ChatClient%20Mock%20Prototype', fileName: 'chat-client-prototype.fig', fileSize: '2.4 MB', createdAt: yesterday.getTime() + 15 * minute },
  ],
  'c-team': [
    { id: 'm-201', conversationId: 'c-team', senderId: 'u-chen', senderName: '陈思远', senderAvatar: avatars.chen, type: 'text', content: '大家记得今晚八点开周会哦。', createdAt: now - 40 * minute },
    { id: 'm-202', conversationId: 'c-team', senderId: 'u-meng', senderName: '孟然', senderAvatar: avatars.meng, type: 'voice', content: '语音消息', duration: 2, transcript: '好的，我会准时参加。', read: false, createdAt: now - 38 * minute },
    { id: 'm-203', conversationId: 'c-team', senderId: 'me', senderName: '我', senderAvatar: avatars.current, type: 'text', content: '收到，我会提前整理好本周进展。', createdAt: now - 31 * minute },
    { id: 'm-204', conversationId: 'c-team', senderId: 'u-chen', senderName: '陈思远', senderAvatar: avatars.chen, type: 'red_packet', content: '项目加油红包', amount: 8.88, createdAt: now - 30 * minute },
    { id: 'm-205', conversationId: 'c-team', senderId: 'u-meng', senderName: '孟然', senderAvatar: avatars.meng, type: 'file', fileKind: 'video', content: '', thumbnail: mockVideoThumbnail(), fileName: '周会演示.mp4', fileSize: '6.8 MB', createdAt: now - 20 * minute },
  ],
}

export function createMockBootstrap(): BootstrapData {
  return {
    user: { id: 'me', nickname: '周朝艳', username: 'zhaoyan_23', avatar: avatars.current, balance: 236.5 },
    friends: [
      { id: 'u-lin', nickname: '林晓', username: 'lin_xiao', avatar: avatars.lin, signature: '把每一天都过成喜欢的样子', status: 'online', remark: '设计组林晓' },
      { id: 'u-meng', nickname: '孟然', username: 'mengran', avatar: avatars.meng, signature: '保持好奇，持续创造', status: 'offline', remark: '大学同学' },
      { id: 'u-chen', nickname: '陈思远', username: 'chensiyuan', avatar: avatars.chen, signature: '代码是写给人看的', status: 'online', remark: '' },
    ],
    friendRequests: [
      { id: 'r-1', nickname: '小满', username: 'xiaoman_hello', avatar: avatar('小', '#ffdfbf'), message: '你好，想和你成为好友', time: '今天 09:32', status: 'pending' },
      { id: 'r-2', nickname: '许知夏', username: 'zhixia', avatar: avatar('许', '#f4d150'), message: '在群里看到你分享的内容，很有帮助！', time: '昨天 16:20', status: 'pending' },
    ],
    groups: [
      { id: 'g-team', groupNumber: '20231001', name: '软件工程 2023 级班通知群', avatar: avatars.code, memberCount: 104, description: '软件工程班级通知与交流', owner: '陈老师', remark: '班级通知群' },
      { id: 'g-design', groupNumber: '20231028', name: '设计交流组', avatar: avatars.design, memberCount: 28, description: '分享灵感，交流设计', owner: '林晓', remark: '设计分享' },
    ],
    conversations: [
      { id: 'c-team', type: 'group', name: '软件工程 2023 级班通知群', avatar: avatars.code, preview: '孟然：[视频] 周会演示.mp4', time: '刚刚', unread: 4, targetId: 'g-team', pinned: true },
      { id: 'c-lin', type: 'direct', name: '林晓', avatar: avatars.lin, preview: '[文件] chat-client-prototype.fig', time: '昨天', unread: 2, targetId: 'u-lin' },
    ],
    messages: structuredClone(baseMessages),
    notifications: [
      { id: 'n-1', type: 'friend_request', title: '新的好友申请', content: '小满请求添加你为好友', time: '今天 09:32', read: false },
      { id: 'n-2', type: 'red_packet', title: '红包领取', content: '陈思远的红包已领取，金额 ¥8.88', time: '昨天 18:16', read: false },
      { id: 'n-3', type: 'friend_accepted', title: '好友申请通过', content: '你已和林晓成为好友', time: '昨天 14:02', read: true },
      { id: 'n-4', type: 'group_joined', title: '加入群聊', content: '你已加入「设计交流组」', time: '周日 20:04', read: true },
      { id: 'n-5', type: 'transfer', title: '转账提醒', content: '本月模拟转账功能已更新', time: '9月 28日', read: true },
      { id: 'n-6', type: 'system', title: '系统通知', content: '欢迎使用 Chatroom，开始和朋友聊天吧', time: '9月 25日', read: true },
    ],
  }
}
