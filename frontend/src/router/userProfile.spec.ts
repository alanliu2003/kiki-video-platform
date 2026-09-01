import { describe, expect, it } from 'vitest'
import { userProfileLocation, userProfilePath } from './userProfile'

describe('user profile route helper', () => {
  it('builds a shared public profile location', () => {
    expect(userProfilePath(3)).toBe('/users/3')
    expect(userProfileLocation(3)).toEqual({ name: 'user-profile', params: { id: '3' } })
  })
})
