import { computed, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { mockTasks } from '../mocks/task.mock'
import type { TaskStatus } from '../types/task'

export const useTaskStore = defineStore('task', () => {
  const tasks = shallowRef([...mockTasks])
  const activeTaskId = shallowRef('task-1001')

  const activeTask = computed(
    () => tasks.value.find((task) => task.id === activeTaskId.value) ?? tasks.value[0],
  )

  function setActiveTask(taskId: string) {
    activeTaskId.value = taskId
  }

  function updateTaskStatus(taskId: string, status: TaskStatus) {
    tasks.value = tasks.value.map((task) => (task.id === taskId ? { ...task, status } : task))
  }

  return {
    activeTask,
    activeTaskId,
    tasks,
    setActiveTask,
    updateTaskStatus,
  }
})
