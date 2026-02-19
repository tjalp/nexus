# JWT Authentication Implementation

This document describes the JWT authentication system implemented for the Nexus backend with GraphQL and SvelteKit frontend.

## Architecture

### Backend (Ktor + GraphQL)

#### Components

1. **Authentication Models** (`common/src/main/kotlin/net/tjalp/nexus/auth/`)
   - `Role.kt` - Enum defining user roles (PLAYER, MODERATOR, ADMIN)
   - `User.kt` - User data class with role-based permission methods
   - `UsersTable.kt` - Database table definition for user credentials
   - `AuthService.kt` - Interface for authentication operations
   - `ExposedAuthService.kt` - Implementation using Exposed ORM with BCrypt password hashing

2. **JWT Configuration** (`backend/src/main/kotlin/net/tjalp/nexus/backend/auth/`)
   - `JwtConfig.kt` - JWT token generation and configuration
     - Access tokens: 15 minutes expiration
     - Refresh tokens: 7 days expiration
   - `AuthRoutes.kt` - REST endpoints for authentication:
     - `POST /auth/login` - User login
     - `POST /auth/register` - User registration
     - `POST /auth/refresh` - Token refresh (TODO)

3. **GraphQL Integration** (`backend/src/main/kotlin/net/tjalp/nexus/backend/schema/`)
   - `GraphQLContext.kt` - Extension functions to access authenticated user from GraphQL context
   - `ProfileSchema.kt` - Protected mutations and filtered queries based on user permissions

#### Protected Operations

**Mutations requiring authentication:**
- `updateGeneralAttachment` - Users can only update their own profile
- `updateNoticesAttachment` - Users can only update their own profile
- `addPunishment` - Requires MODERATOR or ADMIN role
- `removePunishment` - Requires MODERATOR or ADMIN role

**Data filtering:**
- `PunishmentAttachment.punishments` - Users can only view their own punishments unless they are MODERATOR/ADMIN

### Frontend (SvelteKit + URQL)

#### Components

1. **Authentication Store** (`frontend/src/lib/auth.ts`)
   - Svelte store managing auth state
   - localStorage persistence
   - Login/register/logout functions

2. **URQL Client** (`frontend/src/lib/urql-client.ts`)
   - Configured to automatically include JWT token in Authorization header
   - Intercepts all GraphQL requests

3. **Login Page** (`frontend/src/routes/login/+page.svelte`)
   - Example login form
   - Error handling and loading states

## Environment Variables

Set these environment variables for the backend:

```bash
# JWT Configuration
JWT_SECRET=your-secret-key-here  # Change this in production!
JWT_ISSUER=nexus-backend
JWT_AUDIENCE=nexus-client

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/nexus
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
```

## Database Migration

Run the migration to create the users table:

```sql
-- V6__add_users_table.sql is automatically applied by Flyway
```

## Usage Examples

### Backend - Create a User

```kotlin
val user = authService.createUser(
    profileId = UUID.fromString("..."),
    username = "player1",
    password = "securepassword",
    role = Role.PLAYER
)
```

### Backend - Authenticate

```kotlin
val user = authService.authenticate("player1", "securepassword")
if (user != null) {
    val accessToken = JwtConfig.generateAccessToken(user)
    val refreshToken = JwtConfig.generateRefreshToken(user)
}
```

### Frontend - Login

```typescript
import { login } from '$lib/auth';

try {
    const token = await login('player1', 'securepassword');
    console.log('Logged in as:', token.username);
} catch (error) {
    console.error('Login failed:', error);
}
```

### Frontend - Make Authenticated GraphQL Request

```typescript
import { urqlClient } from '$lib/urql-client';
import { gql } from '@urql/svelte';

// The client automatically includes the JWT token
const result = await urqlClient.mutation(gql`
    mutation UpdateProfile($id: UUID!, $name: String!) {
        updateGeneralAttachment(id: $id, lastKnownName: $name) {
            id
        }
    }
`, { id: userId, name: "NewName" });
```

## Security Considerations

1. **Password Storage**: Passwords are hashed using BCrypt with salt
2. **JWT Secret**: Use a strong secret in production (environment variable)
3. **Token Expiration**: Short-lived access tokens reduce risk of token theft
4. **HTTPS**: Always use HTTPS in production
5. **CORS**: Currently allows all origins - restrict in production

## TODO

- [ ] Implement refresh token endpoint
- [ ] Add token blacklist/revocation
- [ ] Add rate limiting to auth endpoints
- [ ] Implement password reset functionality
- [ ] Add email verification
- [ ] Add 2FA support
- [ ] Restrict CORS origins in production
- [ ] Add audit logging for authentication events

## Testing

### Test Authentication Flow

1. Start the backend server
2. Register a new user:
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass","profileId":"<uuid>"}'
```

3. Login:
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'
```

4. Use the access token in GraphQL requests:
```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{"query":"{ profile(id: \"<uuid>\") { id } }"}'
```

