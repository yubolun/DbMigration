import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue')
  },
  {
    path: '/datasources',
    name: 'DataSources',
    component: () => import('../views/DataSources.vue')
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('../views/Tasks.vue')
  },
  {
    path: '/tasks/create',
    name: 'TaskCreate',
    component: () => import('../views/TaskCreate.vue')
  },
  {
    path: '/tasks/edit/:id',
    name: 'TaskEdit',
    component: () => import('../views/TaskCreate.vue'),
    props: true
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('../views/Logs.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
