import { request } from './http'

export async function getCareerProfile() {
  return request('/profile/career')
}

export async function updateCareerProfile(payload) {
  return request('/profile/career', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
