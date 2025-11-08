# PosterPlot 프로젝트

AI 기반 영화 줄거리 생성과 유저 작성 시나리오 열람 기능을 제공하는 웹 플랫폼입니다.  
프론트엔드는 React, 백엔드는 Spring Boot + Flask + MySQL, AI 생성기는 Flask와 HuggingFace 모델로 구성되어 있습니다.

---

## 기획배경 및 설명

많은 사람들이 영화를 보고 직접 자신만의 이야기를 만들어내고 싶어하지만, 막상 시나리오를 작성하는 데에는 많은 시간과 노력이 필요합니다. 이를 해결하기 위해, 영화 포스터를 업로드하면 AI가 시나리오 초안을 자동 생성하고, 사용자가 이를 수정하여 자신만의 시나리오를 작성 및 공유할 수 있는 플랫폼을 개발하게 되었습니다.

- 창작 부담을 줄인 **AI를 이용한 시나리오 초안 생성**
- 시나리오 초안을 수정·확장하여 자신만의 글을 작성하고 공유
- 작성된 시나리오를 공유하고 **다른 사용자와 소통** 가능한 피드백 시스템

이를 통해 전문 지식이 없어도 창작자가 될 수 있도록 부담을 줄이고, 사용자 간의 창의적인 교류를 촉진하는 것을 목표로 했습니다.

- 진행 기간: 2025.01. ~ 2025.03.(3개월)
- 팀 구성 및 담당 역할: 총 3명(프론트엔드 1, **백엔드 1**, AI 모델 1)

---

## 나의 역할 및 구현 내용

- Redis 이용한 회원가입 및 JWT 기반의 로그인
- 사진 업로드 및 시나리오 생성 AI 모델 연동 구현
- 생성된 시나리오 수정 및 게시 기능
- 댓글 및 좋아요 기능 통한 피드백 시스템 구현
- GCP VM 배포 환경 설정 및 서비스 운영

---

## 사용스킬
`Spring Boot`, `Redis`, `Spring Security`, `MySQL`, `GCP Storage`, `Docker`, `GCP`

---

## 프로젝트 구성

| 구성 요소        | 설명                                   | 포트 / 위치                   |
|-----------------|--------------------------------------|------------------------------|
| frontend        | React 기반 프론트엔드                   | `http://localhost:3000`        |
| backend         | Spring Boot 기반 REST API 서버          | `http://localhost:8080`         |
| flask (AI 서비스) | Flask 기반 BLIP + Mistral AI 생성기    | `http://localhost:5000`         |
| db (MySQL)      | 포스터 및 유저 데이터 저장용 DB          | 컨테이너 내부: 3306<br>호스트: **3307** ← 로컬 충돌 방지용 |
| redis           | 이메일 인증 및 확인용 Redis 서버         | `localhost:6379`       |

---
## 환경 변수 파일 안내

- **프론트엔드**: `posterplot_frontend/.env`  
  - 예: `REACT_APP_BACKEND_URL=http://localhost:8080`

- **AI 서비스**: `ai_service/.env`  
  - 예: `HF_TOKEN` (HuggingFace API 토큰) 등
---

## 프로젝트 실행방법

### 프론트엔드 실행 (React)

#### 1. `posterplot_frontend` 디렉토리로 이동

#### 2. `.env` 파일 생성 후 아래 내용 추가

   ```env
   REACT_APP_BACKEND_URL=http://localhost:8080
   ```

#### 3. 의존성 설치

   ```bash
   npm install
   ```

#### 4. 개발 서버 실행

   ```bash
   npm start
   ```


---

### 백엔드 실행 (Spring)

#### 1. IntelliJ IDE 설치

#### 2. JDK 설정

- JDK 17 버전으로 프로젝트를 설정

#### 3. GCP Storage 키 발급

 1. 프로젝트 생성
 2. VM 인스턴스 생성
 3. IAM 및 관리자 > 서비스 계정 > 계정 클릭 후 키 발급
 4. 서비스 계정 > 권한 > 저장소 관리자, 저장소 개체 관리자 추가
 5. 버킷 생성 > src/main/resources/gcp-keys/ 안에 키 저장 후 실행

#### 4. Google 이메일 인증

  1. https://myaccount.google.com/security 에 접속하여 2단계 인증 활성화
  2. 앱 비밀번호를 생성하여 SMTP 인증용 비밀번호 발급
  3. application.properties에 `spring.mail.username`, `spring.mail.password` 수정
     
#### 5. Gradle 빌드

- 터미널 또는 명령 프롬프트에서 다음 명령어를 실행합니다.

```bash
./gradlew build
```

#### 6. Spring Boot 애플리케이션 실행

- 다음 명령어로 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

---

### AI 서비스 (`posterplot_ai.py`) 실행

`posterplot_ai.py`는 Flask 기반 AI 생성기 서버로, BLIP 및 Mistral 모델을 사용합니다.

#### 1. Python 환경 준비

- Python 3.x 권장  
- 가상환경(venv) 생성 및 활성화 (권장)  

   ```bash
   python -m venv venv
   source venv/bin/activate  # Linux/macOS
   venv\Scripts\activate     # Windows
   ```

#### 2. 필수 라이브러리 설치  

```bash
pip install --upgrade pip
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip install transformers pillow requests flask flask-cors mtranslate python-dotenv
```

#### 3. HuggingFace API 키 발급
 https://huggingface.co/ 접속 후 Access Token Read 로 발급
 > .env 폴더 속 HF_TOKEN 키 값 수정 

#### 4. AI 서버 실행  

```bash
python posterplot_ai.py
```



