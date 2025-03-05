import { createRouter, createWebHistory } from 'vue-router';
import LogGrid from './LogGrid.vue';
import RealTimeLog from './RealTimeLog.vue';

const routes = [
  { path: '/', component: LogGrid },
  { path: '/realtime', component: RealTimeLog },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
