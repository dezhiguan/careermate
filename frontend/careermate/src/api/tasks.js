import { request } from './http'

export function listTasks() {
  return request('/tasks')
}

export function createTask(payload) {
  return request('/tasks', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateTask(id, payload) {
  return request(`/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function markTaskDone(id) {
  return request(`/tasks/${id}/done`, { method: 'PUT' })
}

export function markTaskTodo(id) {
  return request(`/tasks/${id}/todo`, { method: 'PUT' })
}

export function deleteTask(id) {
  return request(`/tasks/${id}`, { method: 'DELETE' })
}
