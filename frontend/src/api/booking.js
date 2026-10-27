import request from './request'

export const fetchSlots = () => request.get('/slots')

export const fetchDayBookings = (date) => request.get('/bookings/day', { params: { date } })

export const createBooking = (data) => request.post('/bookings', data)

export const fetchMyBookings = (params) => request.get('/bookings/my', { params })

export const cancelBooking = (id) => request.post(`/bookings/${id}/cancel`)

export const checkinBooking = (id) => request.post(`/bookings/${id}/checkin`)

export const fetchPending = (params) => request.get('/bookings/pending', { params })

export const approveBooking = (id, data) => request.post(`/bookings/${id}/approve`, data)

export const fetchAllBookings = (params) => request.get('/bookings/page', { params })
