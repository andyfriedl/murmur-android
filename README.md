# Murmur

<img src="https://i.imgur.com/x61SFGx.png" width="400" alt="Murmur screenshot">

**The Anti-Social Network**

Murmur is a privacy-first anonymous group chat app for Android built for quick, temporary conversations without accounts, usernames, or persistent identities.

Create a stream, share an invite, and chat. When the stream is deleted, the conversation is gone.

## Why Murmur?

Most chat apps are built around identity, profiles, permanence, and social graphs.

Murmur takes the opposite approach.

- No accounts
- No usernames
- No profiles
- No friend lists
- Temporary group conversations
- Simple invite-based joining

Just ephemeral conversation.

## Features

- Anonymous group chat
- QR code invite-based joining
- Firebase anonymous authentication
- Real-time messaging
- Temporary stream-based conversations
- Stream ownership / creator controls
- Delete stream functionality
- Session-based membership tracking
- Privacy-focused UX and microcopy
- Material 3 Android UI
- Light / dark theme support

## Current Behavior

Messages persist only for the life of the active stream.

When a stream is deleted, messages and associated session data are removed.

Planned future versions may support auto-expiring messages.

## Tech Stack

**Android**
- Kotlin
- Jetpack Compose
- Material 3

**Backend**
- Firebase Realtime Database
- Firebase Anonymous Authentication

**Architecture**
- Repository pattern
- Session state management
- QR invite flow
- Stream-based membership model

## Project Goals

Murmur started as an exploration of privacy-first communication and lightweight real-time group interaction.

The project also serves as a hands-on product design + engineering exercise spanning:

- UX design
- interaction design
- Android development
- Firebase architecture
- real-time messaging flows
- privacy-oriented product thinking

## Status

Active side project / work in progress.

Future exploration may include:

- auto-expiring messages
- improved moderation / cleanup logic
- transport abstraction
- alternative messaging backends

## Screenshots

_Add screenshots here_

## License

Personal project.
