# The feedback receiver

**This is the source of the function that records 👍/👎/zapisz clicks. It is not proof of what is
deployed.** It was committed on 2026-10-19 (F6) from the contract the repository already pinned;
the deployed function predates it. Deploy this one, and the two are the same thing from then on:

```bash
supabase functions deploy feedback --no-verify-jwt
supabase secrets set FEEDBACK_SIGNING_SECRET=<the same value the digest signs with>
```

## Why it is not in the batch

PulseDigest is a single-shot process that exits ([ADR-0002](../docs/adr/0002-hexagonal-architecture.md),
[ADR-0006](../docs/adr/0006-virtual-threads-over-reactive.md)): fetch, synthesise, send, done. A 👍
arrives hours later, from whatever client the reader opened the mail in. Nothing in the batch is
listening, and making it listen would mean a web server running all week to serve a few clicks — the
project's largest architectural change in exchange for its smallest feature. So the batch writes the
links and something else answers them.

That "something else" being absent from the repository was the whole of F6: the loop had a component
nobody could read, test or redeploy from source.

## The contract

```
GET ?url=<item url>&vote=up|down|save&source=<source>&category=<category>&edition=<YYYY-MM-DD>[&sig=<HMAC>]
```

| Obligation | Why it is the receiver's and not the batch's |
|---|---|
| **Uppercase the vote** | The mail emits `vote=up`; the column is `CHECK (vote IN ('UP','DOWN','SAVE'))` and both reading queries match on the uppercase form. Skip this one call and every vote is either rejected by the constraint or silently uncounted. `FeedbackVoteContractIT` exists because this obligation was once written nowhere. |
| **Verify `sig` when a secret is set** | HMAC-SHA256 over `url\|vote\|source\|category\|edition`, URL-safe Base64 without padding, compared in constant time. The vote is lowercased before signing, because that is the form the link carries. |
| **Treat a unique violation as success** | The partial unique index on `(item_url, edition)` is what stops a mail scanner that prefetches all three links from writing three rows. First vote wins; a duplicate is not an error the reader should see. |
| **Answer with a page** | The caller is a person who clicked a link in an email, not an API client. |
| **Never expose the service-role key** | The row is written for an unauthenticated reader, which no anon key may do. The reader's browser only ever receives the HTML. |

`--no-verify-jwt` is required: a mail client carries no Supabase JWT. The HMAC is what authenticates
the request, which is why the signing secret matters more here than anywhere else in the system.

## What a GET with a side effect buys and costs

Email cannot POST. A link is the only thing a reader can click, so the write happens on a GET, and
that is a real cost: anything that follows the link writes a row. Two things make it survivable —
the `edition` parameter with the partial unique index (a prefetch cannot amplify a vote, and cannot
cast a second one later), and the HMAC (a link cannot be forged or re-aimed at another item). What is
*not* defended is a scanner that follows the link exactly once and thereby casts a vote the reader
never cast. That is recorded in [docs/security.md](../docs/security.md) rather than hidden here.

## Tests

```bash
deno test --allow-env receiver/
```

Two assertions, both about the HMAC agreement. They are not part of `./gradlew check` — CI has no
Deno, and a second toolchain for two assertions is a bad trade. They cannot drift from the Java side
regardless: `FeedbackReceiverContractTest` computes the vector with the production signer and asserts
that `index_test.ts` pins the same secret, message and signature, and that `index.ts` builds the
canonical message from the same five fields in the same order. Change one side alone and the Java
build fails.
