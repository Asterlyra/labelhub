import { computed, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { mockProjects } from '../mocks/project.mock'

export const useProjectStore = defineStore('project', () => {
  const projects = shallowRef([...mockProjects])
  const activeProjectId = shallowRef('demo-project')

  const activeProject = computed(
    () => projects.value.find((project) => project.id === activeProjectId.value) ?? projects.value[0],
  )

  function setActiveProject(projectId: string) {
    activeProjectId.value = projectId
  }

  return {
    activeProject,
    activeProjectId,
    projects,
    setActiveProject,
  }
})
