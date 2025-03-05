import { createRouter, createWebHistory } from 'vue-router';
import LogGrid from '../components/LogGrid.vue';
import RealTimeLog from '../components/RealTimeLog.vue';

const routes = [
  { path: '/', component: LogGrid },
  { path: '/realtime', component: RealTimeLog },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
