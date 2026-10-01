import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Friend, FriendRequest, GroupChat } from '../types'

export const useContactsStore = defineStore('contacts', () => {
  const friends = ref<Friend[]>([])
  const friendRequests = ref<FriendRequest[]>([])
  const groups = ref<GroupChat[]>([])
  function setData(nextFriends: Friend[], nextRequests: FriendRequest[], nextGroups: GroupChat[]) {
    friends.value = nextFriends
    friendRequests.value = nextRequests
    groups.value = nextGroups
  }
  function updateRequest(id: string, status: FriendRequest['status']) {
    const request = friendRequests.value.find((item) => item.id === id)
    if (request) request.status = status
  }
  function updateRemark(kind: 'friend' | 'group', id: string, remark: string) {
    const item = kind === 'friend' ? friends.value.find((entry) => entry.id === id) : groups.value.find((entry) => entry.id === id)
    if (item) item.remark = remark
  }
  function addFriend(friend: Friend) {
    if (!friends.value.some((item) => item.id === friend.id)) friends.value.unshift(friend)
  }
  function addGroup(group: GroupChat) {
    if (!groups.value.some((item) => item.id === group.id)) groups.value.unshift(group)
  }
  return { friends, friendRequests, groups, setData, updateRequest, updateRemark, addFriend, addGroup }
})
