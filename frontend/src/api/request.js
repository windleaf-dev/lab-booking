import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装：
 * 1. 自动携带 JWT
 * 2. 后端返回 { code, message, data }，这里直接解包出 data
 * 3. 401 清理登录态并回到登录页
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('lab_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body?.data
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'

    if (status === 401) {
      localStorage.removeItem('lab_token')
      localStorage.removeItem('lab_user')
      if (!window.location.pathname.startsWith('/login')) {
        ElMessage.warning('登录已过期，请重新登录')
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
