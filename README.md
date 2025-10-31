# Spring-Batch

* 일괄 처리 작업을 의미
* 웹 서비스의 실시간 처리(request/response), 절차적 처리와 대비
* 특징:
  1. 대량 데이터 처리 ( 수천 ~ 수백만 건 )
  2. 트랜잭션 단위로 안정적인 처리 가능
  3. 반복적 예약 실행 가능 ( 스케줄링 )

* 등장 배경 :
  1. 대규모 데이터의 처리 문제: 단순 반복 로직(for) 으로 처리하면 메모리 부족, 트랜잭션 실패 발생
  2. 재시도/스킵/트랜잭션 관리 필요: 일부 실패 시 전체 롤백 대신 chunk 단위로 처리
  3. 실행 상태 기록 추적: Job 상태, Step 상태, 실패 데이터 기록 -> 재실행 가능
  4. 유연한 Job 구성: Step 연결, 조건부 흐름, 병렬 처리

<br>  

| 개념 | 설명 | 핵심 역할 |
|------|------|------------|
| Job | 배치 단위 작업 | 여러 Step을 묶어서 관리, 재시작/중단 가능 |
| Step | Job 내부 처리 단계 | Reader → Processor → Writer 구조 |
| Chunk | Step 내부 데이터 처리 단위 | N건씩 트랜잭션 처리 |
| ItemReader | 데이터 소스 읽기 | DB, CSV, JSON, 큐 등 |
| ItemProcessor | 데이터 처리 | 변환, 검증, 필터링 등 |
| ItemWriter | 데이터 출력 | DB, 파일, 메시지 큐 등 |
| JobRepository | 메타데이터 저장 | Job/Step 상태, 재실행 정보 저장 |
| JobLauncher | Job 실행 | 스케줄러, 이벤트에서 Job 호출 |
<br>

### Step 진행 흐름도
<br>
<img width="594" height="327" alt="image" src="https://github.com/user-attachments/assets/f2018f93-e2de-4483-886a-e8f715063a63" />

### Chunk 지향 처리 원리
* Chunk: 한 트랙잭션 단위의 처리

```lua
Reader → Processor → Writer
   ↑        ↑
   └------ Chunk 단위 반복
```
* 예: chunk size 100
  * 100건 읽기 -> 처리 -> 쓰기 -> commit (하나의 트랜잭션으로써 동작)
  *  101번째부터는 새로운 트랜잭션 시작

* 장점
  1. 부분 실패하더라도 일부 chunk만 롤백
  2. 메모리 효율
  3. 트랜잭션 단위의 조절 가능

* Step 흐름과 Job 구성
  1. Sequential Step: Step1 -> Step2 -> step3 (순차 실행)
  2. Conditional Step: Step 종료 상태에 따라 다음 Step 결정

```text
Conditional Step
Step1 [성공] -> Step2 실행
Step1 [실패] -> Step3 실행
```
  3. Parallel Step: Step 병렬 실행 가능 (ThreadPoolTaskExecutor 사용)

### Job Repository와 재실행
JobRepository는 DB 기반의 **메타데이터** 저장소이다.
* 저장 내용:
  * JobInstance, JobExecution
  * StepExecution 상태
  * 실패 항목, 재시도 정보
* 장점
  * Job 실패 시, 재시작 사능
  * 중단된 배치 재개 가능
  * 동일 Job 중복 실행 방지
 
### 예외 처리, 스킵, 재시도
* Step 단위 정책 설정 가능
```java
.faultTolerant()
.skip(Exception.class).skipLimit(10)
.retry(Exception.class).retryLimit(3)
```
* 특정 예외 방생 시, 일부 항목 스킵
* 재시도 제한 설정
* Chunk 단위로 트랜잭션 관리 -> 안정성 확보
