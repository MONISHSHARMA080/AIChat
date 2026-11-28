### AiChat Backend

Cloudflare Worker in `worker/` that lets the user talk to any LLM through Vercel AI Gateway and run a multi-step agent loop with tools and files.

### Architecture

* use `AIChatAgent` from `@cloudflare/ai-chat` for `ChatAgent` — do not drop down to raw `Agent` + `Sessions` and hand-roll HTTP `/turn`, SSE, `ReadableStream` message endpoints, or `turnInFlight` locks
* `AIChatAgent` already handles WebSocket streaming, resumable streams on disconnect, DO eviction recovery, turn queueing, cancellation, SQLite message persistence (`this.messages`), and `GET .../get-messages` — use those primitives
* check the installed docs in `worker/node_modules/agents/docs/` and `worker/node_modules/ai/docs/` before writing code instead of guessing APIs

### Worker handler vs Agent split

* **Worker `fetch()` + `handlers/`** — auth (`withFirebaseAuth`, supporting `Authorization: Bearer` and `?token=` on WebSocket upgrades), REST endpoints (`GET /health`, `GET /models`, `GET /usage`, `POST /auth/sync`, `GET /chats`, `GET /files/:fileId`), and routing `/agents/chat-agent{/:chatId}?{/*}?` to the user's DO (`${user.uid}:${chatId}`) via `getAgentByName`
* the Worker handler only runs for REST calls and the initial WebSocket handshake — it should never stay awake waiting for an AI turn to finish
* **`ChatAgent` (`extends AIChatAgent`)** — only owns the chat turn lifecycle and delegates everything else to `services/` and `repositories/`:
  * `onStart`: store `{ user, chatId }` in DO storage, seed `this.state` with `{ chatId, chat }`, and when `props.chat` exists with empty DO SQLite (`this.messages.length === 0`), hydrate recent messages from D1 via `this.persistMessages(...)`
  * `onChatMessage`: validate `{ model, reasoning }` and check credits via services, offload inline user `data:` files to R2, hydrate recent media + prune model messages (`reasoning: "before-last-message"`, `toolCalls: "before-last-2-messages"`), run `streamText` with tools, pipe through generated-file R2 offloading, and return `createUIMessageStreamResponse({ stream })`
  * `onChatResponse`: deduct actual turn cost via `billingService` (fall back to `this.queue("retryBillingDeduction", ...)` if RevenueCat fails), atomically upsert the chat summary, user + assistant messages (including reasoning trace and `UIMessage` parts), and file rows in D1 only on success, update `this.setState(...)`, rewrite inline user `data:` URLs to `/files/:fileId` via `this.persistMessages(...)`, and clean up R2 files if the turn failed/aborted or D1 failed

### Chats & state

* client connects to `/agents/chat-agent` for a new chat (server generates a `chatId` and sends it in `this.state`) or `/agents/chat-agent/:chatId` for an existing chat
* for an existing `chatId`, verify the chat exists for `user.uid` in D1 (404 otherwise); do not create a `chats` row in D1 until the first turn succeeds (no empty ghost chats)
* D1 holds the global durable store (`chats`, `messages` with `reasoning` + `UIMessage` `parts`, `message_files`); DO SQLite (`this.messages`, capped at `maxPersistedMessages = 200`) holds the active session window and hydrates from D1 on cold start if empty

### Files

* users can send files and the model/tools can generate files
* store file bytes in R2 and keep `/files/:fileId` references in messages (don't leave base64 blobs in DO SQLite); hydrate recent media for the model and let tools read older files from R2

### Code-style

* layered layout: `types/`, `middleware/`, `handlers/`, `services/`, `repositories/`, `agents/`
* keep types in `types/`, SQL in `repositories/`, business logic (billing, models, media) in `services/`, and keep `ChatAgent` thin — no RevenueCat parsing or SQL directly inside the agent class
* do not base backend decisions on the Android app; it's just for seeing the UI and will be updated to match this backend