# Discord integration

The companion pairs with BotysławSmith using `/mobile pair` in the intended Discord server. In the scanner, choose Connect to Discord and redeem the private six-character, one-use code. `/mobile revoke` revokes the device on the server. Local disconnect only removes the phone's credential.

The user reviews seven values before sending: skill tickets, mount keys, hammers, pets/eggs, existing mounts, skill x5 cost and mount cost. Sending updates the paired member/server only. Copying the result creates a `/import` command for manual use on Discord.

Client transport is implemented in `app/src/main/java/eu/komars/forgescanner/sync/MobileSyncClient.kt`. The fixed HTTPS origin is `https://komars.eu/discord/botyslaw-smith/mobile/`. Pairing credentials are encrypted using AndroidKeyStore. Screenshots and recordings are never sent to the backend. Demo mode simulates synchronization locally.

The sibling `polska-moc-bot` repository owns the server API, authorization, validation, limits, idempotency and storage. Its `docs/MOBILE.md` is the authoritative backend protocol reference. Coordinate API changes across repositories without coupling their release versions.
