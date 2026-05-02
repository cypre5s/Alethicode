# Alethicode Java Backend (Migration)

## Prerequisites
- Java 21
- Maven 3.9+
- PostgreSQL 16+ (db: `alethicode`)
- Redis 7+

## Run (dev)
```bash
cd /home/cypress/Alethicode/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Test
```bash
cd /home/cypress/Alethicode/backend
mvn test
```

## Baseline Verification
```bash
/home/cypress/Alethicode/scripts/m12/verify_alethicode_readonly.sh
```

## M1 Contract Smoke
```bash
/home/cypress/Alethicode/scripts/m12/check_m1_contract.sh http://127.0.0.1:8080
```
