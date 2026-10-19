// The receiver's half of the shared HMAC vector. Run with `deno test --allow-env receiver/`.
//
// This file is not in the Java build — CI has no Deno — but it cannot silently drift from it either:
// FeedbackReceiverContractTest asserts that these three constants appear verbatim in this file and
// that the Java signer produces this exact signature for them. Change one side and the Java gate
// fails, which is the only cross-language guarantee available without a second toolchain in CI.

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";

const SECRET = "pulse-digest-test-secret";
const CANONICAL = "https://example.com/item|up|GitHub|Java/JVM|2026-10-19";
const SIGNATURE = "juS2D0bAY7J-mngDVHo_S5s4veZvndJzYNxJJqE4vPc";

async function sign(message: string, secret: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return btoa(String.fromCharCode(...new Uint8Array(mac)))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

Deno.test("the receiver derives the same signature the digest signs with", async () => {
  assertEquals(await sign(CANONICAL, SECRET), SIGNATURE);
});

Deno.test("a flipped vote does not verify", async () => {
  const tampered = CANONICAL.replace("|up|", "|down|");
  assertEquals(await sign(tampered, SECRET) === SIGNATURE, false);
});
