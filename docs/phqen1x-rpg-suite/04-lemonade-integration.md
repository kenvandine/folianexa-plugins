# 04 — Lemonade integration

The inference client both plugins share: endpoints, prompting, getting JSON out
of a model that will not promise you JSON, and not melting the one box that
serves everything.

## The server

[Lemonade Server](https://lemonade-server.ai/) exposes an OpenAI-compatible API.
The deployment this design targets runs on a dedicated host on the local
network on **port 13305** — which is Lemonade's own default port, so the
configured default is also the out-of-the-box one.

The base URL is configurable and must be, since the host varies per deployment:

```yaml
lemonade:
  base-url: "http://lemonade.local:13305"
  api-path: "/api/v1"
```

Lemonade accepts both `/api/v1/*` and `/v1/*`; `/api/v1` is the canonical form
and the default here. `api-path` is separately configurable so an unusual
reverse-proxy layout does not require patching the plugin.

### Endpoints used

| Endpoint | Used for |
| --- | --- |
| `POST {api-path}/chat/completions` | Everything. All generation, all stages, all repair rounds. |
| `GET {api-path}/models` | `/wec status`, and resolving a blank `lemonade.model` to whatever is loaded. |

Lemonade offers a great deal more — embeddings, audio, vision, image generation.
None of it is used. If a later milestone wants embeddings for semantic library
search, that is one more endpoint and no architectural change.

### Parameters

Per Lemonade's documented support: `messages`, `model`, `stream`, `stop`,
`temperature`, `top_p`, `top_k`, `repeat_penalty`, `max_tokens` /
`max_completion_tokens`, and `tools`. `logprobs` is not available.

**`response_format` is not documented as supported**, and this shapes the entire
client design. There is no `json_schema` mode to lean on and no grammar
constraint to guarantee well-formed output. The plugin must assume it will
sometimes receive prose, sometimes fenced code, sometimes JSON with a trailing
comma, and sometimes a cheerful paragraph about what a nice building it has
designed for you.

The client probes for `response_format` support once at startup — sends a
trivial request with `response_format: {"type": "json_object"}` and notes
whether the server errors — and uses it when available. **The extract-validate-
repair path runs regardless.** Structured output is treated as a helpful
accident, never as a guarantee.

Streaming is not used. There is no partial output worth showing for a JSON
document that is worthless until complete, and non-streaming keeps the client
simple.

## The client

`LemonadeClient` follows `folianexa-stats`'s `HttpMgmtClient` closely, because
that class already encodes a hard-won lesson.

```java
this.httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
        .build();
```

**Pin HTTP/1.1.** `HttpMgmtClient.java:44-63` documents the reason at length: the
JDK client prefers HTTP/2, which over plaintext means attempting an
`Upgrade: h2c` handshake on every request. Against a uvicorn/h11 server that did
not fail cleanly — it intermittently corrupted request framing on reused
connections, producing alternating "422 Field required, input: null" (an empty
body arriving) and bare "400 Invalid HTTP request" from uvicorn. Lemonade Server
is also a uvicorn application. Pin the version and the upgrade attempt never
happens.

Request shape:

```java
HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + apiPath + "/chat/completions"))
        .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
        .header("Content-Type", "application/json")
        // Authorization only when api-key is non-blank
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
```

Rules carried over from `HttpMgmtClient`:

- **No `org.bukkit` imports**, but real I/O — callers must only invoke it from
  `Bukkit.getAsyncScheduler()`. Stated in the class javadoc, as it is there.
- **Failures are returned, not thrown.** A generation that fails should tell the
  operator why, not propagate an exception into a scheduler thread.
- `InterruptedException` re-sets the interrupt flag.
- Request and response bodies are logged at `FINE`, so `/wec status` and a
  debug log are enough to diagnose a bad prompt without attaching a debugger.

Timeouts are generous. `request-timeout-seconds` defaults to **180** — a 30B
model on a busy box producing 4,000 tokens of JSON is not fast, and a timeout
that fires mid-generation wastes the whole call.

## Getting JSON out

Four layers, each cheaper than the one after it.

### 1. Ask properly

The system prompt ends with an unambiguous instruction to emit one JSON object
and nothing else — no explanation, no markdown fence, no preamble. This alone
handles most requests from a well-behaved instruct model.

### 2. Extract

`JsonCoercion` assumes the instruction was ignored. It:

- strips ```` ```json ```` fences if present;
- scans for the first `{` and walks forward tracking brace depth **while
  respecting string literals and escapes** — a naive brace count breaks on any
  block-state string containing a brace, and on any prose containing one;
- returns the outermost balanced object;
- if that fails, retries from the last `{` in the response, which catches the
  common "here is my reasoning… now here is the JSON" shape.

### 3. Parse forgivingly

`MiniJson` — ported from `folianexa-stats` — parses in a mode that tolerates the
things models actually do: trailing commas, single-quoted strings, unquoted
keys, and `//` comments. Strict-mode parsing of model output would send
perfectly recoverable responses back for another expensive round.

### 4. Validate and repair

Structural parsing succeeding says nothing about the content. `BuildScriptValidator`
(for structures) or `StageValidator` (for campaign stages) checks semantics and
returns a list of issues, each carrying a **model-readable message** phrased as
an instruction.

The repair round appends to the same conversation:

```
Your previous response had problems. Fix them and return the corrected
complete JSON object.

- Operation 4 uses palette key "collumn", which is not defined. Defined
  keys are: wall, trim, floor, mossy, pillar, lava, light.
- Operation 9 uses block "minecraft:dwarven_forge_block", which does not
  exist. Use a real Minecraft block ID.
- size is [180, 40, 180]. The maximum for any axis is 128.

Return only the corrected JSON object.
```

Specific, enumerated, quoting real values and real limits. Vague feedback
("your JSON was invalid") produces another invalid response; this shape usually
gets a fix in one round.

`lemonade.max-attempts` (default 3) means one initial call plus two repairs.
Past that a model that is going to fail keeps failing, and each round costs a
full inference. On final failure the operator gets the accumulated issues and
the raw output is kept under `failed/` for inspection.

## Prompting

### The system prompt is generated, not written

For structure generation, the system prompt is built from `OpRegistry` at
startup. Adding an operation adds it to the parser, the validator and the prompt
in one edit.

A hand-maintained prompt drifts from the implementation within a month, and the
failure mode is nasty: the model confidently emits an operation that no longer
exists, and it looks like a model problem rather than a documentation problem.

Structure, in order:

1. Role: you generate Minecraft structures as JSON build scripts.
2. The document shape, with one complete worked example.
3. The operation list with fields, generated from the registry.
4. **The active caps as literal numbers** — pulled from live config, not
   hardcoded. A model told "max 128" and then rejected at 128 is being
   mistreated.
5. Guidance: prefer bulk ops; use `repeat` rather than repeating yourself; use
   `noise_replace` and `carve` so it does not look extruded; place markers.
6. The output instruction.

### Campaign stages

Nine prompts, one per stage ([`02-rpg-design.md`](02-rpg-design.md#campaign-generation)).
Each carries a **compacted** summary of prior stages — names and structure, not
prose. Stage 5 (quests) needs the region names, site names, NPC names and act
outline; it does not need the world bible's three paragraphs on the pantheon.

Keeping carried context small is what keeps a local model coherent by stage 9,
and `CampaignGeneratorTest` asserts the carried context stays under budget
precisely because this is the thing that will silently regress.

Every stage prompt enumerates its enum vocabulary explicitly — the eight
objective types, the boss phase mechanics, the valid roles — because a model
given a closed list picks from it, and a model given a free field invents
`ESCORT_BUT_STEALTH` and fails validation.

### Sampling

`temperature: 0.4`, `top_p: 0.9`. Low, deliberately. Creativity in a build
script means malformed geometry, not interesting geometry; the interesting part
comes from the prompt.

Campaign *prose* stages (1, 4) may use a higher temperature — `0.8` — since
their output is text where variety is the point. This is a per-stage override,
not a global setting.

## The shared queue

One inference box serves both plugins. `InferenceQueue` sits in front of
`LemonadeClient` and is published through the API artifact
([`05-shared-api.md`](05-shared-api.md)) so **the RPG plugin uses the same
instance**.

Without this, a campaign generation running its nine stages while an operator
types `/wec generate` gives you two pipelines fighting for one GPU, and both get
slower than either alone would be.

- `max-concurrent-requests` (default 2) in flight.
- `queue-capacity` (default 32) waiting; submissions past that are rejected
  immediately with a clear message rather than queued into next week.
- FIFO, with one exception: an interactive `/wec generate` from a player
  outranks a background campaign stage. An operator watching a progress bar
  should not wait behind a batch job.
- Queue depth and current wait are reported by `/wec status`.

## Operations

`/wec status` is the diagnostic:

```
Lemonade  http://lemonade.local:13305  reachable (42ms)
Model     Qwen3-Coder-30B-A3B-Instruct-GGUF  (loaded)
          3 models available
Queue     1 in flight, 0 waiting
Last call 18.4s, 3,204 tokens, ok
Last error  (none)
Structured output  not supported — using repair loop
```

Failure modes and what they look like:

| Symptom | Likely cause |
| --- | --- |
| Connection refused | Server down, or `base-url` wrong. `/wec status` says so immediately. |
| 404 on `/chat/completions` | `api-path` wrong — try `/v1`. |
| Timeouts on every call | Model too large for the hardware, or `request-timeout-seconds` too low for the token count. |
| Valid JSON, nonsense geometry | A prompt or model-choice problem, not a plumbing problem. Try a larger or more instruction-tuned model before touching the code. |
| Every request fails validation identically | The model is not following the schema. Check `failed/` — the raw output usually makes the reason obvious in seconds. |
| Intermittent 400/422 with empty bodies | The h2c framing bug. Confirm HTTP/1.1 is actually pinned. |

### Model choice

`lemonade.model` blank means "ask `/api/v1/models` and take the first", which
works for a single-model box. Pin it once you know which model behaves.

Build-script generation is a code-generation task — a structured schema with
strict syntax — and instruction-tuned code models tend to do better at it than
general chat models of the same size. Campaign prose is the opposite. A
deployment with two models loaded may want a per-purpose override; the config
shape allows it (`generation.model`, `campaign.stage-models`) even though the
first milestone will not use it.

## Test plan

| Test | Asserts |
| --- | --- |
| `LemonadeClientTest` | Against a real `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0`, the pattern from `folianexa-stats/src/test/java/.../HttpMgmtClientTest.java`. The exact request body sent. Handling of 200, 404, 500, malformed JSON, empty body, and connection refused. That nothing throws. |
| `JsonCoercionTest` | Extracts from a fenced block, prose-then-JSON, JSON-then-prose, and JSON containing braces inside strings. Fails cleanly on no JSON. Handles the last-`{` fallback. |
| `MiniJsonTest` | Ported from `folianexa-stats`, plus the forgiving-mode cases: trailing commas, single quotes, unquoted keys, comments. |
| `RepairLoopTest` | A fake server returning garbage, then prose, then valid JSON. Exactly three requests. Rounds 2 and 3 carry the prior validation errors verbatim. `max-attempts` honoured. Final failure surfaces accumulated issues. |
| `PromptBuilderTest` | The generated prompt contains every registered op, and the caps in it match live config rather than constants. |
| `InferenceQueueTest` | Concurrency cap respected. Capacity rejection is immediate. Interactive requests jump background ones. Shutdown drains cleanly. |
| `StructuredOutputProbeTest` | A server that 400s on `response_format` results in the fallback path; one that accepts it results in the parameter being sent. Either way the repair loop stays wired in. |

## What's real vs. unverified

Grounded: Lemonade's endpoints, its default port of 13305, its parameter
support, and the absence of documented `response_format` support all come from
its [published API documentation](https://lemonade-server.ai/docs/api/openai/).
The HTTP/1.1 pinning is not speculative — it is a live-confirmed bug documented
in this repo at `HttpMgmtClient.java:44-63`, against the same server stack.

Unverified: everything about behaviour. No request has been made to a Lemonade
server from this design. Unknown and important — how reliably a given model
produces valid build scripts on the first attempt; whether the repair loop
converges in two rounds or usually exhausts its budget; real latency per call;
whether `max-concurrent-requests: 2` is right for the hardware; and whether
`response_format` in fact works despite being undocumented. That last one is a
five-minute check with `curl` and worth doing before writing the client.

## License

MIT
