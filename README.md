# ResumeAI — Spring Boot Backend

## Project Structure

```
resumeai-backend/
├── pom.xml
└── src/main/
    ├── java/com/resumeai/
    │   ├── ResumeAiApplication.java        # Entry point
    │   ├── config/
    │   │   └── SecurityConfig.java         # Spring Security + CORS + JWT
    │   ├── controller/
    │   │   ├── AuthController.java         # Register, Login, /me
    │   │   ├── ResumeController.java       # Generate, Download PDF
    │   │   └── PaymentController.java      # Razorpay create + verify
    │   ├── service/
    │   │   ├── ClaudeService.java          # Secure Claude API proxy
    │   │   ├── PaymentService.java         # Razorpay logic + credit system
    │   │   └── PdfService.java             # iText7 PDF generation
    │   ├── model/
    │   │   ├── User.java                   # User entity
    │   │   ├── UserRepository.java
    │   │   ├── Order.java                  # Payment order entity
    │   │   └── OrderRepository.java
    │   ├── dto/
    │   │   ├── AuthDto.java                # Register/Login request+response
    │   │   ├── ResumeDto.java              # Generate request+response
    │   │   └── PaymentDto.java             # Razorpay request+response
    │   └── security/
    │       ├── JwtUtil.java                # JWT generate + validate
    │       └── JwtAuthFilter.java          # Request auth filter
    └── resources/
        └── application.properties          # All config keys
```

---

## API Endpoints

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, get JWT |
| GET  | `/api/auth/me` | Get current user info |

### Resume
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/resume/generate` | Generate resume/cover letter (costs credits) |
| POST | `/api/resume/download/resume` | Download resume as PDF |
| POST | `/api/resume/download/cover` | Download cover letter as PDF |
| GET  | `/api/resume/credits` | Get user credit balance |

### Payment
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/payment/create-order` | Create Razorpay order |
| POST | `/api/payment/verify` | Verify payment + add credits |

---

## Setup & Run

### 1. Prerequisites
- Java 17+
- Maven 3.8+

### 2. Configure application.properties
```properties
# Fill in your keys:
claude.api.key=sk-ant-xxxx
razorpay.key.id=rzp_live_xxxx
razorpay.key.secret=your_secret
jwt.secret=your-very-long-secret-key-min-32-chars
```

### 3. Run locally (H2 in-memory DB)
```bash
mvn spring-boot:run
```
Server starts at http://localhost:8080
H2 console at http://localhost:8080/h2-console

### 4. Switch to PostgreSQL (production)
In `application.properties`, comment out H2 block and uncomment PostgreSQL:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/resumeai
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### 5. Build JAR for deployment
```bash
mvn clean package -DskipTests
java -jar target/resumeai-backend-1.0.0.jar
```

---

## Payment Flow (Razorpay)

```
Frontend                    Backend                     Razorpay
   |                           |                            |
   |-- POST /create-order ----->|                            |
   |                           |-- Create Order ----------->|
   |                           |<-- razorpay_order_id ------|
   |<-- order_id + key_id -----|                            |
   |                           |                            |
   |-- Open Razorpay Checkout--|                            |
   |                           |                            |
   |-- User pays ------------->|                            Razorpay
   |<-- payment_id + sig ------|                            |
   |                           |                            |
   |-- POST /verify ---------->|                            |
   |                           |-- Verify HMAC signature    |
   |                           |-- Mark order PAID          |
   |                           |-- Add credits to user      |
   |<-- credits added ---------|                            |
```

---

## Frontend Integration (React)

### 1. Register / Login
```js
// Register
const res = await fetch('http://localhost:8080/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password, name })
});
const { token } = await res.json();
localStorage.setItem('token', token);

// Login
const res = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});
```

### 2. Create Razorpay Order + Open Checkout
```js
const token = localStorage.getItem('token');

// Create order
const orderRes = await fetch('http://localhost:8080/api/payment/create-order', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ plan: 'BUNDLE' })
});
const { razorpayOrderId, amount, keyId } = await orderRes.json();

// Open Razorpay checkout
const rzp = new Razorpay({
  key: keyId,
  amount,
  currency: 'INR',
  order_id: razorpayOrderId,
  handler: async (response) => {
    // Verify on backend
    await fetch('http://localhost:8080/api/payment/verify', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        razorpayOrderId: response.razorpay_order_id,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature
      })
    });
    // Now generate!
  }
});
rzp.open();
```

### 3. Generate Resume
```js
const res = await fetch('http://localhost:8080/api/resume/generate', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ name, role, experience, skills, plan: 'BUNDLE', tone: 'Professional' })
});
const { resume, coverLetter, creditsRemaining } = await res.json();
```

### 4. Download PDF
```js
const res = await fetch('http://localhost:8080/api/resume/download/resume', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ name, role, experience, skills })
});
const blob = await res.blob();
const url = URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url; a.download = 'resume.pdf'; a.click();
```

---

## Deploy to Railway / Render

1. Push to GitHub
2. Connect Railway/Render to your repo
3. Set environment variables:
   - `CLAUDE_API_KEY`
   - `RAZORPAY_KEY_ID`
   - `RAZORPAY_KEY_SECRET`
   - `JWT_SECRET`
   - `SPRING_DATASOURCE_URL` (PostgreSQL from Railway)
4. Done — auto-deploys on every push
