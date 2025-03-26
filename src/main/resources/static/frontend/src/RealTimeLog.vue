<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import axios from 'axios';
import moment from 'moment';

// 실시간 로그를 저장할 배열
const logs = ref([]);

// 폴링 인터벌 ID를 저장할 변수
let pollIntervalId = null;

// 기본 폴링 간격 (밀리초 단위)
const POLL_INTERVAL_DEFAULT = 10000; // 10초
const pollingInterval = ref(POLL_INTERVAL_DEFAULT);

// 사용 가능한 폴링 간격 옵션 (예: 5초, 10초, 30초)
const pollingOptions = [
  { label: "5초", value: 5000 },
  { label: "10초", value: 10000 },
  { label: "30초", value: 30000 }
];

// 서버 선택 변수 (기본: "web")
const selectedServer = ref("web");

// 화면에 처음 진입한 시각을 기록 (moment 객체)
const screenEntryTime = moment();

// 현재 폴링 시작 시각 (화면 진입 시각을 pollingInterval 단위로 내림 처리)
const currentPollStart = ref(
  moment(Math.floor(screenEntryTime.valueOf() / pollingInterval.value) * pollingInterval.value)
);

/**
 * 백엔드 API를 호출하여 현재 인터벌(예: [startTime, endTime])에 해당하는 로그만 가져옵니다.
 * startTime과 endTime은 ISO 형식(예: "2025-03-26 12:48:00", "2025-03-26 12:48:09.999")으로 전송됩니다.
 */
const pollLogs = async () => {
  // 현재 폴링 구간 계산
  const startTime = currentPollStart.value.format("YYYY-MM-DD HH:mm:ss");
  // 끝 시각: 시작 시각 + pollingInterval - 1 밀리초
  const endTime = currentPollStart.value.clone().add(pollingInterval.value, "milliseconds").subtract(1, "milliseconds").format("YYYY-MM-DD HH:mm:ss.SSS");

  try {
  console.log('poll')
    const response = await axios.get('http://localhost:8099/api/logs/poll', {
      params: {
        startTime,
        endTime,
        server: selectedServer.value
      }
    });
    // 응답을 줄 단위로 분리하고, 빈 줄은 제외
    const newLogs = response.data.split('\n').filter(line => line.trim() !== "");
    // 새로운 로그가 있을 경우에만 추가
    if(newLogs.length > 0) {
      logs.value.push(...newLogs);
    }
    // 다음 폴링 구간: 현재 시작 시각에 pollingInterval을 더함
    currentPollStart.value = currentPollStart.value.clone().add(pollingInterval.value, "milliseconds");
  } catch (error) {
    console.error("로그 폴링 실패:", error);
  }
};

/**
 * 현재 선택된 폴링 간격으로 폴링을 시작합니다.
 * 기존 인터벌이 있다면 해제 후, 새 인터벌을 설정합니다.
 */
const startPolling = () => {
  if (pollIntervalId) {
    clearInterval(pollIntervalId);
  }
  // 초기 폴링 호출
  pollLogs();
  pollIntervalId = setInterval(pollLogs, pollingInterval.value);
};

// 서버 선택 또는 폴링 간격 변경 시, 로그 배열 초기화하고 폴링 구간 재설정 후 새 인터벌로 폴링 시작
watch([selectedServer, pollingInterval], () => {
  logs.value = [];
  const now = moment();
  // 현재 시간을 pollingInterval 단위로 내림 처리하여 현재 폴링 시작 시각을 재설정
  currentPollStart.value = moment(Math.floor(now.valueOf() / pollingInterval.value) * pollingInterval.value);
  startPolling();
});

// 컴포넌트가 마운트되면 폴링 시작, 언마운트 시 인터벌 정리
onMounted(() => {
  startPolling();
});
onUnmounted(() => {
  if (pollIntervalId) {
    clearInterval(pollIntervalId);
  }
});

/**
 * 현재까지 수집된 로그를 하나의 텍스트 파일로 만들어 클라이언트에서 다운로드하도록 트리거하는 함수입니다.
 * 파일명은 "realtime_YYYYMMDD_HHMMSS.txt" 형식으로 생성됩니다.
 */
const downloadRealtimeLogs = () => {
  const logContent = logs.value.join('\n');
  const blob = new Blob([logContent], { type: 'text/plain' });
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  const hour = String(now.getHours()).padStart(2, '0');
  const minute = String(now.getMinutes()).padStart(2, '0');
  const second = String(now.getSeconds()).padStart(2, '0');
  const filename = `realtime_${year}${month}${day}_${hour}${minute}${second}.txt`;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  URL.revokeObjectURL(url);
};
</script>

<template>
  <div class="container">
    <h1 class="header">실시간 로그 (폴링 방식)</h1>

    <!-- 옵션 패널 -->
    <div class="options">
      <div class="option-item">
        <label for="serverSelect">서버 선택:</label>
        <select id="serverSelect" v-model="selectedServer">
          <option value="web">웹 서버</option>
          <option value="deliveryapp">배송기사앱</option>
        </select>
      </div>
      <div class="option-item">
        <label for="pollIntervalSelect">폴링 간격:</label>
        <select id="pollIntervalSelect" v-model.number="pollingInterval">
          <option v-for="option in pollingOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>
      <div class="option-item">
        <button class="btn download-btn" @click="downloadRealtimeLogs">실시간 로그 다운로드</button>
      </div>
    </div>

    <!-- 로그 뷰어 영역 -->
    <div class="log-viewer">
      <ul class="log-list">
        <li v-for="(log, index) in logs" :key="index">{{ log }}</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
/* 전체 컨테이너 스타일 */
.container {
  width: 100%;
  margin: 0;
  padding: 1.5rem;
  background-color: #ffffff;
}

/* 헤더 스타일 */
.header {
  text-align: center;
  margin-bottom: 1.5rem;
  font-size: 1.8rem;
  color: #333;
}

/* 옵션 패널 스타일 */
.options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}

.option-item {
  margin-bottom: 0.5rem;
}

.option-item label {
  margin-right: 0.5rem;
  font-weight: bold;
  color: #555;
}

select {
  padding: 0.4rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

/* 버튼 스타일 */
.btn {
  padding: 0.5rem 1rem;
  background-color: #007bff;
  color: #ffffff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.3s ease;
}

.btn:hover {
  background-color: #0056b3;
}

.download-btn {
  margin-left: 1rem;
}

/* 로그 뷰어 스타일 */
.log-viewer {
  background-color: #f8f9fa;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 1rem;
  max-height: 800px;
  overflow-y: auto;
}

.log-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.log-list li {
  padding: 0.4rem 0;
  border-bottom: 1px solid #eee;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9rem;
  color: #333;
}

.log-list li:last-child {
  border-bottom: none;
}
</style>
