![Nexus Logo](assets/nexus-logo.png)

A Minecraft server plugin providing various utilities and features with a modern web-based management interface.

## Features

- **Player Profile Management**: Track player data, preferences, and settings
- **Punishment System**: Comprehensive moderation tools with warnings, mutes, bans, and kicks
- **JWT Authentication**: Secure role-based access control for the web interface
- **GraphQL API**: Modern API for frontend integration
- **Role-Based Authorization**: PLAYER, MODERATOR, and ADMIN roles with granular permissions

## Authentication System

This project includes a complete JWT-based authentication system for the backend API. Key features:

- 🔐 Secure password hashing with BCrypt
- 🎫 JWT token-based authentication (15-minute access tokens)
- 👥 Role-based access control (PLAYER, MODERATOR, ADMIN)
- 🛡️ Protected GraphQL mutations
- 🔍 Privacy-filtered data access (users only see their own punishments unless moderator/admin)

**Quick Start Guide**: See [`QUICK_START.md`](QUICK_START.md) for setup instructions.
**Full Documentation**: See [`AUTH_IMPLEMENTATION.md`](AUTH_IMPLEMENTATION.md) for complete details.
**GraphQL Examples**: See [`GRAPHQL_EXAMPLES.md`](GRAPHQL_EXAMPLES.md) for API usage.

## Building and Running

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew plugin:runServer` to build and run the plugin a Minecraft server.
* Run `./gradlew build` to only build the plugin.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).