/**
 * 实时通知 WebSocket 客户端。
 *
 * 设计要点：
 * 1. 只有登录后才连接，URL 携带 JWT（浏览器 WebSocket 不能自定义请求头）
 * 2. 断线自动重连，指数退避，最长 30 秒
 * 3. 25 秒发一次 ping，服务端回 pong，用于发现半开连接
 * 4. 消息与连接状态分别订阅，页面卸载时记得解绑
 */

let socket = null
let currentToken = ''
let reconnectTimer = null
let heartbeatTimer = null
let retryCount = 0
let manuallyClosed = false

const messageHandlers = new Set()
const stateHandlers = new Set()
let state = 'offline'

function setState(next) {
  if (state === next) return
  state = next
  stateHandlers.forEach((handler) => {
    try {
      handler(next)
    } catch {
      // 单个订阅者异常不影响其他订阅者
    }
  })
}

function buildUrl(token) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/notifications?token=${encodeURIComponent(token)}`
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(() => {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send('ping')
    }
  }, 25_000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function scheduleReconnect() {
  if (manuallyClosed || !currentToken) return
  retryCount += 1
  const delay = Math.min(30_000, 1000 * 2 ** Math.min(retryCount, 5))
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  reconnectTimer = window.setTimeout(() => connectSocket(currentToken), delay)
}

export function connectSocket(token) {
  if (!token) return
  currentToken = token
  manuallyClosed = false

  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }

  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  setState('connecting')
  const ws = new WebSocket(buildUrl(token))
  socket = ws

  ws.onopen = () => {
    retryCount = 0
    setState('online')
    startHeartbeat()
  }

  ws.onmessage = (event) => {
    let message
    try {
      message = JSON.parse(event.data)
    } catch {
      return
    }
    if (!message || message.type === 'PONG') return
    messageHandlers.forEach((handler) => {
      try {
        handler(message)
      } catch {
        // 忽略单个订阅者异常
      }
    })
  }

  ws.onerror = () => {
    // onclose 会紧接着触发，这里只负责关闭坏连接
    try {
      ws.close()
    } catch {
      // ignore
    }
  }

  ws.onclose = () => {
    stopHeartbeat()
    if (socket === ws) socket = null
    setState('offline')
    scheduleReconnect()
  }
}

export function closeSocket() {
  manuallyClosed = true
  currentToken = ''
  retryCount = 0
  stopHeartbeat()
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (socket) {
    try {
      socket.close()
    } catch {
      // ignore
    }
    socket = null
  }
  setState('offline')
}

export function onSocketMessage(handler) {
  messageHandlers.add(handler)
  return () => messageHandlers.delete(handler)
}

export function onSocketState(handler) {
  handler(state)
  stateHandlers.add(handler)
  return () => stateHandlers.delete(handler)
}
