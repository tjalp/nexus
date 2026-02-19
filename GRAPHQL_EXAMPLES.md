# GraphQL Authentication Examples

This file contains example GraphQL queries and mutations for testing the authentication system.

## Setup

All authenticated requests require the Authorization header:
```
Authorization: Bearer <your-access-token>
```

## Queries

### Get Profile (No auth required for basic info)
```graphql
query GetProfile($id: UUID!) {
  profile(id: $id) {
    id
    createdAt
    modifiedAt
    attachments {
      ... on GeneralAttachment {
        lastKnownName
        preferredLocale
        timeZone
      }
      ... on NoticesAttachment {
        acceptedRulesVersion
        seenRecommendations
      }
      ... on PunishmentAttachment {
        # This will filter based on your permissions
        punishments {
          type
          severity
          reason
          timestamp
          issuedBy
          caseId
          isActive
          expiresAt
        }
      }
    }
  }
}
```

Variables:
```json
{
  "id": "00000000-0000-0000-0000-000000000001"
}
```

## Mutations (Require Authentication)

### Update General Attachment (Own Profile Only)
```graphql
mutation UpdateGeneral($id: UUID!, $name: String) {
  updateGeneralAttachment(
    id: $id
    lastKnownName: $name
  ) {
    id
    attachments {
      ... on GeneralAttachment {
        lastKnownName
      }
    }
  }
}
```

Variables:
```json
{
  "id": "your-profile-uuid",
  "name": "New Display Name"
}
```

### Update Notices (Own Profile Only)
```graphql
mutation UpdateNotices($id: UUID!, $rulesVersion: Int, $seenRecs: Boolean) {
  updateNoticesAttachment(
    id: $id
    acceptedRulesVersion: $rulesVersion
    seenRecommendations: $seenRecs
  ) {
    id
    attachments {
      ... on NoticesAttachment {
        acceptedRulesVersion
        seenRecommendations
      }
    }
  }
}
```

Variables:
```json
{
  "id": "your-profile-uuid",
  "rulesVersion": 2,
  "seenRecs": true
}
```

### Add Punishment (MODERATOR/ADMIN Only)
```graphql
mutation AddPunishment(
  $id: UUID!
  $type: PunishmentType!
  $severity: PunishmentSeverity!
  $reason: String!
  $issuedBy: String
  $duration: Duration
) {
  addPunishment(
    id: $id
    type: $type
    severity: $severity
    reason: $reason
    issuedBy: $issuedBy
    duration: $duration
  ) {
    type
    severity
    reason
    timestamp
    issuedBy
    caseId
    isActive
  }
}
```

Variables:
```json
{
  "id": "player-profile-uuid",
  "type": "WARNING",
  "severity": "MINOR",
  "reason": "Breaking server rules",
  "issuedBy": "ModeratorName"
}
```

### Remove Punishment (MODERATOR/ADMIN Only)
```graphql
mutation RemovePunishment($id: UUID!, $caseId: String!) {
  removePunishment(id: $id, caseId: $caseId) {
    type
    reason
    isActive
  }
}
```

Variables:
```json
{
  "id": "player-profile-uuid",
  "caseId": "ABC123DEF456"
}
```

## Testing Scenarios

### 1. Test as Regular Player
Login as a PLAYER and try:
- ✅ View own profile
- ✅ Update own general attachment
- ✅ Update own notices
- ✅ View own punishments
- ❌ View other players' punishments (should return empty)
- ❌ Add punishment (should fail with error)
- ❌ Remove punishment (should fail with error)

### 2. Test as Moderator
Login as a MODERATOR and try:
- ✅ View any profile
- ✅ View all punishments
- ✅ Add punishments to any player
- ✅ Remove punishments from any player
- ❌ Update other players' profiles (should fail)

### 3. Test as Administrator
Login as an ADMIN and try:
- ✅ All operations should work

## Error Responses

### No Authentication
```json
{
  "errors": [
    {
      "message": "Authentication required"
    }
  ]
}
```

### Insufficient Permissions
```json
{
  "errors": [
    {
      "message": "You do not have permission to modify this profile"
    }
  ]
}
```

### Moderator-Only Action
```json
{
  "errors": [
    {
      "message": "Only moderators and administrators can add punishments"
    }
  ]
}
```

## Using with curl

### With authentication:
```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{"query":"query { profile(id: \"UUID\") { id } }"}'
```

### PowerShell:
```powershell
$token = "your-access-token"
$body = @{
  query = "query { profile(id: \`"UUID\`") { id } }"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/graphql" `
  -Method Post `
  -Headers @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
  } `
  -Body $body
```

## Using GraphQL Playground

1. Navigate to: `http://localhost:8080/graphql`
2. In the bottom left, click "HTTP HEADERS"
3. Add your auth header:
```json
{
  "Authorization": "Bearer YOUR_ACCESS_TOKEN"
}
```
4. Run your queries/mutations

## Enum Values

### PunishmentType
- `BAN`
- `MUTE`
- `WARNING`
- `KICK`

### PunishmentSeverity
- `WARNING`
- `MINOR`
- `MODERATE`
- `MAJOR`
- `SEVERE`
- `CRITICAL`

### Role (for reference)
- `PLAYER` - Regular user
- `MODERATOR` - Can manage punishments, view all data
- `ADMIN` - Full access

## Tips

1. **Save your access token** after login for reuse
2. **Tokens expire after 15 minutes** - you'll need to login again
3. **Use variables** in GraphQL Playground for easier testing
4. **Check the response** for detailed error messages
5. **Use the GraphQL Playground schema explorer** to discover available fields

