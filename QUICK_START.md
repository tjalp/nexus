# Quick Start Guide - JWT Authentication

## Prerequisites
- PostgreSQL database running
- Java/Kotlin environment configured
- Node.js for frontend

## Step 1: Sync Dependencies

```powershell
cd C:\Users\Ties\Documents\Code\nexus
.\gradlew --refresh-dependencies
```

## Step 2: Set Environment Variables (Optional)

For development, defaults will work. For production:

```powershell
# Windows PowerShell
$env:JWT_SECRET = "your-super-secret-key-change-in-production"
$env:JWT_ISSUER = "nexus-backend"
$env:JWT_AUDIENCE = "nexus-client"
```

## Step 3: Start Backend

```powershell
.\gradlew :backend:run
```

The database migration will automatically create the `users` table.

## Step 4: Create First User

### Option A: Using curl (after backend is running)

First, you need a profile UUID. You can create one through your existing profile creation flow, or register directly:

```powershell
curl -X POST http://localhost:8080/auth/register `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"admin\",\"password\":\"admin123\",\"profileId\":\"<your-profile-uuid>\"}'
```

### Option B: Directly in Database

```sql
-- First, create a profile if you don't have one
INSERT INTO profiles (id) VALUES (gen_random_uuid()) RETURNING id;

-- Then create a user (use the profile id from above)
-- Password 'admin123' hashed with BCrypt
INSERT INTO users (id, profile_id, username, password_hash, role) 
VALUES (
  gen_random_uuid(),
  '<profile-id>',
  'admin',
  '$2a$10$...',  -- Use BCrypt to hash 'admin123'
  'ADMIN'
);
```

## Step 5: Test Authentication

### Login
```powershell
curl -X POST http://localhost:8080/auth/login `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"admin\",\"password\":\"admin123\"}'
```

Response:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "userId": "...",
  "username": "admin",
  "role": "ADMIN"
}
```

### Use Token in GraphQL

```powershell
$token = "your-access-token-here"

curl -X POST http://localhost:8080/graphql `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $token" `
  -d '{\"query\":\"query { profile(id: \\\"<uuid>\\\") { id } }\"}'
```

### Test Protected Mutation

```powershell
curl -X POST http://localhost:8080/graphql `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $token" `
  -d '{\"query\":\"mutation { addPunishment(id: \\\"<user-id>\\\", type: WARNING, severity: MINOR, reason: \\\"Test warning\\\") { type reason } }\"}'
```

## Step 6: Frontend Setup

### Install Frontend Dependencies
```powershell
cd frontend
npm install
# or
bun install
```

### Start Frontend Dev Server
```powershell
npm run dev
# or
bun run dev
```

### Visit Login Page
Navigate to: `http://localhost:5173/login`

## Testing the Full Flow

1. **Register a new user**:
   - POST to `/auth/register` with username, password, and profileId
   
2. **Login**:
   - POST to `/auth/login` with username and password
   - Save the `accessToken` and `refreshToken`
   
3. **Make authenticated GraphQL requests**:
   - Add header: `Authorization: Bearer <accessToken>`
   - Try protected mutations like `updateGeneralAttachment`
   
4. **Test authorization**:
   - Try to modify another user's profile (should fail)
   - Try to view another user's punishments as PLAYER (should return empty)
   - Login as MODERATOR/ADMIN and view all punishments (should work)

## Common Issues

### "Unresolved reference" errors
Run: `.\gradlew --refresh-dependencies` and restart your IDE

### "Table 'users' doesn't exist"
The migration should run automatically. Check:
- Flyway is configured correctly
- Database connection is working
- Migration file `V6__add_users_table.sql` exists

### "Token is not valid or has expired"
- Check if JWT_SECRET matches between token generation and validation
- Verify token hasn't expired (15 min for access tokens)
- Ensure Authorization header format: `Bearer <token>`

### CORS errors in frontend
- Verify backend CORS configuration allows your frontend origin
- Check Authorization header is allowed in CORS

## Default Credentials

**⚠️ IMPORTANT: Change these in production!**

After creating your first admin user:
- Username: `admin`
- Password: `admin123`
- Role: `ADMIN`

## API Endpoints

### Authentication
- `POST /auth/login` - Login
- `POST /auth/register` - Register new user
- `POST /auth/refresh` - Refresh access token (TODO)

### GraphQL
- `POST /graphql` - GraphQL endpoint (requires auth for mutations)
- `GET /graphql` - GraphQL Playground

### Documentation
- `GET /swagger` - Swagger UI
- `GET /openapi` - OpenAPI spec

## Next Steps

1. Implement refresh token endpoint
2. Add password reset flow
3. Create admin panel for user management
4. Add audit logging
5. Implement rate limiting
6. Add email verification

## Resources

- Full documentation: `AUTH_IMPLEMENTATION.md`
- Implementation details: `IMPLEMENTATION_SUMMARY.md`
- Code examples in created files

