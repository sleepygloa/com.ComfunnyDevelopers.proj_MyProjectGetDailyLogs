<script setup>
import { RouterLink, RouterView } from 'vue-router';
</script>

<template>
  <nav>
    <RouterLink to="/">📜 전날 로그</RouterLink> |
    <RouterLink to="/realtime">⏳ 실시간 로그</RouterLink>
  </nav>
  <RouterView />
</template>
