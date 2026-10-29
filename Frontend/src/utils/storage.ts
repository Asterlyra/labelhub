export function readStorage(key: string) {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeStorage(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // 浏览器禁用本地存储时不阻塞演示流程。
  }
}

export function removeStorage(key: string) {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // 浏览器禁用本地存储时不阻塞演示流程。
  }
}
