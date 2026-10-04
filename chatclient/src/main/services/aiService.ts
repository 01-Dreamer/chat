import { createHash } from 'node:crypto'
import { databaseManager } from '../database/databaseManager'
import { apiClient } from './apiClient'

class AiService {
  async translate(text: string, targetLanguage = '简体中文') {
    const hash = createHash('sha256').update(text).digest('hex')
    const cached = databaseManager.getTranslation(hash, targetLanguage)
    if (cached) return cached
    const result = await apiClient.post<{ translatedText: string }>('/ai/translate', { text, targetLanguage })
    databaseManager.saveTranslation(hash, targetLanguage, result.translatedText)
    return result.translatedText
  }

  async smartReplies(messages: Array<{ role: 'user' | 'assistant', content: string }>) {
    const result = await apiClient.post<{ suggestions: string[] }>('/ai/smart-replies', { messages: messages.slice(-12) })
    return result.suggestions
  }

  async transcribe(resourceId: string) {
    let resource = databaseManager.getFileResource(resourceId)
    if (!resource) throw new Error('本地没有语音资源元数据')
    const cached = databaseManager.getAsr(resource.fileHash)
    if (cached) return cached
    const result = await apiClient.post<{ text: string }>(`/asr/${resourceId}`)
    databaseManager.saveAsr(resource.fileHash, result.text)
    return result.text
  }
}

export const aiService = new AiService()
