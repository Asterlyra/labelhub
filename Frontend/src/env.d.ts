/// <reference types="vite/client" />

import type { UserRole } from './types/user'

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    description?: string
    immersive?: boolean
    public?: boolean
    roles?: UserRole[]
  }
}
