import request from './request'

export const fetchOverview = () => request.get('/stats/overview')
export const fetchDeviceByCategory = () => request.get('/stats/device-by-category')
export const fetchStatusDistribution = () => request.get('/stats/status-distribution')
export const fetchBookingTrend = (days = 7) => request.get('/stats/booking-trend', { params: { days } })
export const fetchTopDevices = () => request.get('/stats/top-devices')
