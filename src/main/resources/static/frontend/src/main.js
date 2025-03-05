import { createApp } from 'vue';
import App from './App.vue';
import router from './router'; // 🔥 `router` 불러오기

const app = createApp(App);
app.use(router);
app.mount('#app');
