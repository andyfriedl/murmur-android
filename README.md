# Murmur

<img src="https://i.imgur.com/x61SFGx.png" width="400" alt="Murmur screenshot">

**The Anti-Social Network**

Murmur is a privacy-first anonymous group chat app for Android built for quick, temporary conversations without accounts, usernames, profiles, or persistent identities.

Create a stream, share a QR code, chat, then disappear.

## Download

Murmur is currently available as a signed Android APK while Play Store access is pending.

Download the latest release from GitHub:

[Download Murmur APK](https://github.com/andyfriedl/murmur-android/releases/latest)

Android may ask you to allow installs from your browser or file manager.

## Why Murmur?

Most chat apps are built around identity, profiles, permanence, and social graphs.

Murmur takes the opposite approach.

* No accounts
* No usernames
* No profiles
* No friend lists
* QR-based joining
* Temporary group conversations
* Simple stream ownership controls

Just lightweight, private conversation.

## Features

* Anonymous group chat
* QR code invite-based joining
* Firebase anonymous authentication
* Realtime encrypted messaging
* Temporary stream-based conversations
* Stream ownership / creator controls
* Delete stream functionality
* Session-based membership tracking
* Privacy-focused UX and microcopy
* Material 3 Android UI
* Light / dark theme support

## Current Behavior

* Messages are handled through MurmurRelay.
* Firebase is used as the realtime transport and presence layer.
* Users join streams through QR codes.
* Stream creators can delete a stream.
* Joiners can leave and rejoin with a valid invite.
* Messages are encrypted through the MurmurRelay layer before being sent through Firebase.
* No usernames, profiles, or persistent social identity are used.

## MurmurRelay

Murmur uses [MurmurRelay](https://github.com/andyfriedl/murmur-relay), a transport-agnostic encrypted messaging layer.

MurmurRelay handles:

* shared channel key generation
* message encryption and decryption
* send / observe messaging flow
* pluggable transports such as Firebase or future relay backends

This keeps Murmur’s app code focused on the chat experience while encryption and message transport live behind a reusable SDK boundary.

## Tech Stack

### Android

* Kotlin
* Jetpack Compose
* Material 3

### Backend / Transport

* Firebase Realtime Database
* Firebase Anonymous Authentication
* MurmurRelay SDK
* Firebase-backed MurmurRelay transport

### Architecture

* Repository pattern
* ViewModel-driven stream state
* Session state management
* QR invite flow
* Stream-based membership model
* Transport abstraction for encrypted messaging

## Project Goals

Murmur started as an exploration of privacy-first communication and lightweight realtime group interaction.

The project also serves as a hands-on product design and engineering exercise spanning:

* UX design
* interaction design
* Android development
* Firebase architecture
* realtime messaging flows
* privacy-oriented product thinking
* encrypted transport abstraction

## Status

Active side project.

Current focus:

* testing signed APK distribution
* keeping the app simple and privacy-focused
* exploring future MurmurRelay transport options

Future exploration may include:

* auto-expiring messages
* improved stream cleanup rules
* alternative relay backends
* offline or local-network relay concepts
* stronger abuse protection and App Check

## Privacy

Murmur does not use usernames, profiles, friend lists, or persistent social identity.

See the privacy policy:

* [Privacy Policy](https://www.vvork.org/murmur/murmur-privacy)

## Related Projects

* [MurmurRelay](https://github.com/andyfriedl/murmur-relay)

## More of My Projects

See more of my work at [VVORK Studios](https://www.vvork.org/).

## License

Personal project.
