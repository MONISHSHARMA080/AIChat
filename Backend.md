### AiChat Backend

Cloudflare Worker in `worker/` that lets the user talk to any LLM through Vercel AI Gateway and run a multi-step agent loop with tools and files.

### Architecture

* use `AIChatAgent` from `@cloudflare/ai-chat` for `ChatAgent` — do not drop down to raw `Agent` + `Sessions` and hand-roll HTTP `/turn`, SSE, `ReadableStream` message endpoints, or `turnInFlight` locks
* `AIChatAgent` already handles WebSocket streaming, resumable streams on disconnect, DO eviction recovery, turn queueing, cancellation, SQLite message persistence (`this.messages`), and `GET .../get-messages` — use those primitives
* check the installed docs in `worker/node_modules/agents/docs/` and `worker/node_modules/ai/docs/` before writing code instead of guessing APIs

### Worker handler vs Agent split

* **Worker `fetch()` + `handlers/`** — auth (`withFirebaseAuth`), REST endpoints (`GET /models`, `GET /usage`, `POST /auth/sync`, `GET /chats`, `GET /chats/:chatId/messages`, `GET /files/:fileId`), and routing `/agents/chat-agent/:chatId` to the user's DO (`${user.uid}:${chatId}`) via `getAgentByName`
* the Worker handler only runs for REST calls and the initial WebSocket handshake — it should never stay awake waiting for an AI turn to finish
* **`ChatAgent` (`extends AIChatAgent`)** — only owns the chat turn lifecycle and delegates everything else to `services/` and `repositories/`:
  * `onChatMessage`: validate `{ model, reasoning }` and check credits via services, prepare model messages + tools, run `streamText`, and return `createUIMessageStreamResponse({ stream: toUIMessageStream(...) })`
  * `onChatResponse`: deduct actual turn cost via `billingService` (fall back to `this.queue("retryBillingDeduction", ...)` if RevenueCat fails), upsert the chat summary + file rows in D1 only on success, update `this.setState(...)`, and clean up R2 files if the turn failed/aborted

### Chats & state

* client connects to `/agents/chat-agent/:chatId` (fresh UUID for a new chat, existing `chatId` to continue one)
* reject if that `chatId` already belongs to another user in D1; do not create a `chats` row in D1 until the first turn succeeds (no empty ghost chats)
* DO SQLite (`this.messages`) is the source of truth for conversation history; D1 holds the user/chat index (`chats`) and file metadata (`message_files`)

### Files

* users can send files and the model/tools can generate files
* store file bytes in R2 and keep `/files/:fileId` references in messages (don't leave base64 blobs in DO SQLite); hydrate recent media for the model and let tools read older files from R2

### Code-style

* layered layout: `types/`, `middleware/`, `handlers/`, `services/`, `repositories/`, `agents/`
* keep types in `types/`, SQL in `repositories/`, business logic (billing, models, media) in `services/`, and keep `ChatAgent` thin — no RevenueCat parsing or SQL directly inside the agent class
* do not base backend decisions on the Android app; it's just for seeing the UI and will be updated to match this backend