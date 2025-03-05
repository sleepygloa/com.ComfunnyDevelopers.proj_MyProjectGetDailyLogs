<template>
  <div class="container">
    <h1 class="header">로그 파일 목록 (2025-03-01 이후)</h1>

    <!-- 옵션 패널: 서버 선택 -->
    <div class="option-panel">
      <div class="option-item">
        <label for="serverSelect">서버 선택:</label>
        <select id="serverSelect" v-model="selectedServer">
          <option value="web">웹 서버</option>
          <option value="deliveryapp">배송기사앱</option>
        </select>
      </div>
    </div>

    <!-- 로그 목록 테이블 -->
    <div class="card">
      <table class="log-table">
        <thead>
          <tr>
            <th>#</th>
            <th>날짜</th>
            <th>다운로드 상태</th>
            <th>다운로드</th>
            <th>보기</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in gridRows" :key="row.date">
            <td>{{ index + 1 }}</td>
            <td>{{ row.displayDate }}</td>
            <td class="status">{{ row.status }}</td>
            <td>
              <!-- 파일이 다운로드되지 않은 경우("X")에만 다운로드 버튼 보이기 -->
              <button v-if="row.status === 'X'" class="btn" @click="downloadLogFromServer(row)">
                다운로드
              </button>
            </td>
            <td>
              <!-- 파일이 다운로드된 경우에만 보기 버튼 활성화 -->
              <button class="btn" @click="viewLogFromServer(row)" :disabled="row.status === 'X'">
                보기
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 로그 내용 영역 -->
    <div class="card log-area">
      <h2>로그 내용 (전체)</h2>
      <pre class="log-content">{{ selectedLogContent }}</pre>
    </div>

    <!-- 에러 로그 영역 -->
    <div class="card log-area">
      <h2>에러 로그</h2>
      <pre class="log-content error">{{ errorLogs }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import axios from 'axios';
import moment from 'moment';

// 시작 날짜 및 날짜 포맷 설정
const START_DATE = "2025-03-01";
const dateFormatForDisplay = "YYYY-MM-DD";
const dateFormatForApi = "YYYYMMDD";

// 서버 선택 변수 (기본: 웹 서버)
const selectedServer = ref("web");

// 상태 변수: 각 행은 { date, displayDate, status }
// status: "X" (다운로드 안됨) 또는 "" (다운로드 완료)
const gridRows = ref([]);

// 선택된 로그 내용을 보여주기 위한 변수
const selectedLogContent = ref("");

// 날짜 범위 생성 (2025-03-01부터 오늘까지, 최신 날짜가 위에 오도록)
const fetchDateRange = () => {
  const start = moment(START_DATE, "YYYY-MM-DD");
  const today = moment();
  const dates = [];
  let current = start.clone();
  while (current.isSameOrBefore(today, 'day')) {
    dates.push(current.clone());
    current.add(1, 'days');
  }
  gridRows.value = dates
    .reverse()
    .map(date => ({
      date: date.format(dateFormatForApi),
      displayDate: date.format(dateFormatForDisplay),
      status: "X"
    }));
};

// 각 날짜별로 서버에 로그 파일이 존재하는지 체크하는 함수
const checkFileStatus = async (row) => {
  try {
    const response = await axios.get(`http://localhost:8080/api/logs/fileStatus`, {
      params: { date: row.date, server: selectedServer.value }
    });
    row.status = response.data.exists ? "" : "X";
  } catch (error) {
    console.error(`파일 상태 체크 실패 (날짜: ${row.date})`, error);
    row.status = "X";
  }
};

// 전체 날짜 목록에 대해 파일 상태 업데이트
const updateFileStatuses = async () => {
  for (const row of gridRows.value) {
    await checkFileStatus(row);
  }
};

// 개별 다운로드 함수: 백엔드의 파일 다운로드 API 호출
const downloadLogFromServer = async (row) => {
  try {
    const response = await axios.get(`http://localhost:8080/api/logs/downloadFile`, {
      params: { date: row.date, server: selectedServer.value },
      responseType: 'blob'
    });
    const blob = new Blob([response.data], { type: response.headers['content-type'] });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `log_${row.date}.txt`;
    document.body.appendChild(a);
    a.click();
    URL.revokeObjectURL(url);
    // 다운로드 성공하면 상태를 공백으로 업데이트
    row.status = "";
  } catch (error) {
    console.error("다운로드 실패:", error);
    alert("해당 날짜의 로그 다운로드에 실패했습니다.");
  }
};

// 개별 로그 보기 함수: 백엔드의 로그 내용 API 호출
const viewLogFromServer = async (row) => {
  try {
    const response = await axios.get(`http://localhost:8080/api/logs`, {
      params: { date: row.date, server: selectedServer.value }
    });
    selectedLogContent.value = response.data;
  } catch (error) {
    console.error("로그 보기 실패:", error);
    selectedLogContent.value = '로그를 불러오는데 실패했습니다.';
  }
};

// 에러 로그만 추출하는 computed property
const errorLogs = computed(() => {
  if (!selectedLogContent.value) return "";
  return selectedLogContent.value
    .split("\n")
    .filter(line => /error|exception/i.test(line))
    .join("\n");
});

// 서버 선택 변경 시, 날짜별 파일 상태 업데이트와 선택된 로그 내용 초기화
watch(selectedServer, async () => {
  await updateFileStatuses();
  selectedLogContent.value = "";
});

// 초기 로딩: 날짜 범위 생성 후 각 날짜별 파일 상태 체크
onMounted(async () => {
  fetchDateRange();
  await updateFileStatuses();
});
</script>

<style scoped>
/* 컨테이너 전체 스타일 */
.container {
  width: 100%;
  padding: 1rem;
  background-color: #f0f2f5;
}

/* 헤더 스타일 */
.header {
  text-align: center;
  margin-bottom: 1.5rem;
  font-size: 2rem;
  color: #333;
}

/* 옵션 패널 스타일 */
.option-panel {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.option-item {
  margin: 0 0.5rem;
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

/* 카드 스타일: 옵션, 테이블, 로그 영역 등에 사용 */
.card {
  background-color: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  padding: 1rem;
  margin-bottom: 1.5rem;
}

/* 테이블 스타일 */
.log-table {
  width: 100%;
  border-collapse: collapse;
}

.log-table th,
.log-table td {
  padding: 0.75rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.log-table th {
  background-color: #f7f7f7;
  font-weight: bold;
  color: #333;
}

.log-table tr:hover {
  background-color: #f1f1f1;
}

.status {
  text-align: center;
}

/* 버튼 스타일 */
.btn {
  padding: 0.4rem 0.8rem;
  background-color: #007bff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.3s ease;
}

.btn:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.btn:hover:not(:disabled) {
  background-color: #0056b3;
}

/* 로그 영역 스타일 */
.log-area h2 {
  margin-bottom: 0.75rem;
  font-size: 1.4rem;
  color: #333;
}

.log-content {
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.95rem;
  line-height: 1.5;
  background-color: #f7f7f7;
  padding: 0.75rem;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.log-content.error {
  background-color: #fff0f0;
  border-color: #ffcccc;
}
</style>
