import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DanmakuOverlay from './DanmakuOverlay.vue'

describe('DanmakuOverlay', () => {
  it('renders content as text and pauses animation when requested', () => {
    const wrapper = mount(DanmakuOverlay, {
      props: {
        paused: true,
        items: [
          {
            key: '1',
            id: 1,
            videoId: 9,
            user: { id: 2, username: 'bob', displayName: 'Bob' },
            content: '<script>alert(1)</script>',
            videoTimeMs: 1000,
            style: 'NORMAL',
            createdAt: '2026-08-28T08:00:00Z',
          },
        ],
      },
    })
    expect(wrapper.classes()).toContain('is-paused')
    expect(wrapper.get('.danmaku-item').text()).toBe('<script>alert(1)</script>')
    expect(wrapper.html()).not.toContain('<script>alert(1)</script></script>')
  })
})
