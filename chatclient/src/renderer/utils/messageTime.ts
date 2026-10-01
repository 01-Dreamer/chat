import type { Message } from '../types'

/** 相邻消息达到这个间隔后，重新显示一条时间分隔。 */
export const MESSAGE_TIME_GAP_MS = 5 * 60 * 1000

export function shouldShowMessageTime(messages: Message[], index: number) {
  if (index === 0) return true
  return messages[index].createdAt - messages[index - 1].createdAt >= MESSAGE_TIME_GAP_MS
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

export function formatMessageTime(timestamp: number, now = new Date()) {
  const date = new Date(timestamp)
  const clock = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  const sameDay = date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()

  if (sameDay) return clock
  if (date.getFullYear() === now.getFullYear()) return `${date.getMonth() + 1}月${date.getDate()}日 ${clock}`
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${clock}`
}
