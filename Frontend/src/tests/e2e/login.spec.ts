import { expect, test } from '@playwright/test'

test('shows the role-based login screen', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '选择角色，进入对应的数据标注流程。' })).toBeVisible()
  await expect(page.getByRole('button', { name: /进入系统/ })).toBeVisible()
})
