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
  function addRequest(request: FriendRequest) {
    const index = friendRequests.value.findIndex((item) => item.id === request.id)
    if (index >= 0) friendRequests.value[index] = request
    else friendRequests.value.unshift(request)
  }
  function setFriends(nextFriends: Friend[]) { friends.value = nextFriends }
  function setGroups(nextGroups: GroupChat[]) { groups.value = nextGroups }
  function removeRequest(id: string) {
    friendRequests.value = friendRequests.value.filter((item) => item.id !== id)
  }
  function updateRemark(kind: 'friend' | 'group', id: string, remark: string) {
    const item = kind === 'friend' ? friends.value.find((entry) => entry.id === id) : groups.value.find((entry) => entry.id === id)
    if (item) item.remark = remark
  }
  function addFriend(friend: Friend) {
    if (!friends.value.some((item) => item.id === friend.id)) friends.value.unshift(friend)
  }
  function removeFriend(id: string) {
    friends.value = friends.value.filter((item) => item.id !== id)
  }
  function addGroup(group: GroupChat) {
    if (!groups.value.some((item) => item.id === group.id)) groups.value.unshift(group)
  }
  function updateGroup(group: GroupChat) {
    const index = groups.value.findIndex((item) => item.id === group.id)
    if (index >= 0) groups.value[index] = group
  }
  function removeGroup(id: string) { groups.value = groups.value.filter((item) => item.id !== id) }
  function reset() {
    friends.value = []
    friendRequests.value = []
    groups.value = []
  }
  return { friends, friendRequests, groups, setData, setFriends, setGroups, addRequest, updateRequest, removeRequest, updateRemark, addFriend, removeFriend, addGroup, updateGroup, removeGroup, reset }
})
