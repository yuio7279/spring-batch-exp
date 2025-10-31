# Spring Batch

* 일괄 처리 작업을 의미
* 웹 서비스의 실시간 처리(request/response), 절차적 처리와 대비
* 특징:
  1. 대량 데이터 처리 ( 수천 ~ 수백만 건 )
  2. 트랜잭션 단위로 안정적인 처리 가능
  3. 반복적 예약 실행 가능 ( 스케줄링 )

* 등장 배경:
  1. 대규모 데이터의 처리 문제: 단순 반복 로직(for) 으로 처리하면 메모리 부족, 트랜잭션 실패 발생
  2. 재시도/스킵/트랜잭션 관리 필요: 일부 실패 시 전체 롤백 대신 chunk 단위로 처리
  3. 실행 상태 기록 추적: Job 상태, Step 상태, 실패 데이터 기록 -> 재실행 가능
  4. 유연한 Job 구성: Step 연결, 조건부 흐름, 병렬 처리

* 사용 예시:
  1. DB 내 대량 데이터를 한 번에 수정 (만 나이 폐지로 전체 회원의 나이를 낮추는 작업, 금융권에서 특정 시키에 계좌에 이자 지급 등)
  2. 누적 로그 데이터를 수집해 분석 작업

### Spring batch 특징
* 병렬처리 방법의 배치 전용 프레임워크
* 특징:
  * 실패 시 처리
  * 상태 추적
  * 확장성 ( Application, Batch Core, Batch Infrastucture )
 ---
### Application layer
가장 상위의 계층으로, 우리가 작성하는 비즈니스 로직에 해당한다. 이자를 계산하거나, 데이터 계산, 로그 분석 등 개발자가 개입하는 개층이다.

### Batch Core layer ( 배치 엔진 )
배치 작업을 실행/관리하는 기능이 포함된 계층, Job, Step, Job launcher, Job Repository 등이 속해있다.
 
<br>  

| 개념 | 설명 | 핵심 역할 |
|------|------|------------|
| Job | 배치 단위 작업 | 여러 Step을 묶어서 관리, 재시작/중단 가능 |
| Step | Job 내부에서 실제 데이터를 처리하는 단계 | Reader → Processor → Writer 구조 |
| Chunk | Step 내부 데이터 처리 단위 | N건씩 트랜잭션 처리 |
| ItemReader | 데이터 소스 읽기 | DB, CSV, JSON, 큐 등 |
| ItemProcessor | 데이터 처리 | 변환, 검증, 필터링 등 |
| ItemWriter | 데이터 출력 | DB, 파일, 메시지 큐 등 |
| JobRepository | 실행 정보(메타데이터) 저장 | Job/Step 상태, 재실행 정보 저장, 모니터링 가능 |
| JobLauncher | Job 실행시키는 주체 | 스케줄러, 이벤트에서 Job 호출 |
<br>

* JobExecution: Job 실행 상태를 나타냄( 시작, 진행, 완료 등)
* StepExecution: Step 실행 상태와 진행 상황을 나타냄, 몇 건을 처리했는지, 성공/실패 여부 등
<br>
  
* Job은 여러 Step의 묶음 -> JobExecution이 상태를 관리한다.
* Step은 실제 처리 단위 -> StepExecution으로 진행 상황을 관리한다.
<br>

### Batch Infrastructure layer
* 배치 구조에서 가장 하위 계층
* 실제로 **데이터를 읽고 쓰는 역할** 담당
* Job/Step에서 정의된 로직이 이 계층을 통해 실제로 처리됨
  
---
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

---
## 코드 흐름 간단한 예시
```java
ItemReader
@Bean
public FlatFileItemReader<Person> reader() {
    return new FlatFileItemReaderBuilder<Person>()
            .name("personReader")
            .resource(new ClassPathResource("data.csv"))
            .delimited()
            .names("id", "name", "age")
            .targetType(Person.class)
            .build();
}
```
* data.csv 파일을 읽고, 한 줄 씩 Person객체로 매핑, 필드는 id, name, age

```java
ItemProcessor
@Bean
public ItemProcessor<Person, Person> processor() {
    return person -> {
        // 나이 +1 처리 (예시)
        person.setAge(person.getAge() + 1);
        return person;
    };
}
```
* person 객체의 age 필드를 가져와 나이를 + 1 처리

```java
ItemWriter
@Bean
public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<Person>()
            .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
            .sql("INSERT INTO person (id, name, age) VALUES (:id, :name, :age)")
            .dataSource(dataSource)
            .build();
}
```
* 처리된 데이터를 DB에 저장한다.
* BeanPropertyItemSqlParameterSourceProvider 사용 → 객체 필드 매핑

```java
Step
@Bean
public Step step1(StepBuilderFactory stepBuilderFactory,
                  ItemReader<Person> reader,
                  ItemProcessor<Person, Person> processor,
                  ItemWriter<Person> writer) {
    return stepBuilderFactory.get("step1")
            .<Person, Person>chunk(2) // 2건씩 트랜잭션 처리
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
}
```
* Chunk 단위는 2건씩 처리하도록 설정
* Reader -> Processor -> Writer 순으로 진행


```java
Job
@Bean
public Job importUserJob(JobBuilderFactory jobBuilderFactory,
                         Step step1) {
    return jobBuilderFactory.get("importUserJob")
            .start(step1)
            .build();
}
```
* Job은 여러 Step의 묶음이다.

```java
jobLauncher.run(...)
@Autowired
private JobLauncher jobLauncher;

@Autowired
private Job importUserJob;

public void runJob() throws Exception {
    jobLauncher.run(importUserJob, new JobParametersBuilder()
            .addLong("time", System.currentTimeMillis())
            .toJobParameters());
}
```
* JobLauncher를 통해 배치 실행 가능
