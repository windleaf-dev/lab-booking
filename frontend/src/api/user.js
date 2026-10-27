import request from './request'

export const fetchUsers = (params) => request.get('/users/page', { params })
export const createUser = (data) => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const resetUserPassword = (id, password) =>
  request.post(`/users/${id}/reset-password`, { password })
