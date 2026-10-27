import request from './request'

export const fetchDevices = (params) => request.get('/devices/page', { params })
export const fetchDevice = (id) => request.get(`/devices/${id}`)
export const createDevice = (data) => request.post('/devices', data)
export const updateDevice = (id, data) => request.put(`/devices/${id}`, data)
export const removeDevice = (id) => request.delete(`/devices/${id}`)
