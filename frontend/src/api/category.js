import request from './request'

export const fetchCategories = () => request.get('/categories')
export const createCategory = (data) => request.post('/categories', data)
export const updateCategory = (id, data) => request.put(`/categories/${id}`, data)
export const removeCategory = (id) => request.delete(`/categories/${id}`)
