package dev.gounthar.xcpng.ide.xo

import java.net.MalformedURLException
import java.net.URI
import java.net.URL

/**
 * Builds a [URL] without the constructor deprecated in Java 20, and without changing what the
 * caller sees when the address is bad.
 *
 * ## Why this is not a one-line swap
 *
 * The obvious replacement for `URL(spec)` is `URI.create(spec).toURL()`. Measured on this
 * host, that is not behaviour-preserving in two ways, and one of them is silent.
 *
 * **The exception type changes.** `URL(String)` throws [MalformedURLException], which is
 * checked and an `IOException`. `URI.create` throws `IllegalArgumentException`, which is
 * neither, and `URI.toURL()` throws it again for a URI that is not absolute. `xoUnreachableMessage`
 * has an explicit `is MalformedURLException ->` branch carrying the guided "it should be the
 * appliance's own address and nothing more" line, and an `IllegalArgumentException` falls
 * through every branch to the bare `else`. So a straight swap would have silently deleted the
 * best message in that function, which exists precisely because a JDK message is useless on a
 * settings form. Hence the mapping below.
 *
 * **Whitespace stops being tolerated.** `URL(String)` accepts leading whitespace and a space
 * in the middle of a path; `URI.create` refuses both. The base address is typed by a human
 * into a settings field, so this is not hypothetical. That half is NOT fixed here, because it
 * cannot be: by the time a spec reaches this function the base and the path are already
 * concatenated. It is fixed at the source, by trimming `baseUrl` in the client constructors.
 *
 * ## The measured table
 *
 * Probed with `java UrlProbe.java` on this host, 2026-08-29, building the spec exactly the way
 * the clients do (`base.trimEnd('/') + "/rest/v0" + path`):
 *
 * ```
 * base as typed          spec built                            URL(String)      URI.create
 * https://host           https://host/rest/v0/vms              OK               OK
 * "https://host "        "https://host /rest/v0/vms"           MalformedURL     IllegalArgument
 * " https://host"        " https://host/rest/v0/vms"           OK               IllegalArgument
 * https://host/          https://host/rest/v0/vms              OK               OK
 * "https://host/ "       "https://host/ /rest/v0/vms"          OK               IllegalArgument
 * not a url              not a url/rest/v0/vms                 MalformedURL     IllegalArgument
 * ```
 *
 * **Note the second row, because it corrects the report that prompted this change.** A trailing
 * space was described as working today and about to stop. That is true of `URL(base)` on its
 * own, and false here: this code never builds a URL from the base alone, so the space lands in
 * the middle and `URL(String)` already refuses it. Trimming makes that case start working
 * rather than preserving it. The rows that genuinely regress under a naive swap are the leading
 * space and the trailing-slash-then-space, and neither was in the original report.
 */
internal fun urlOf(spec: String): URL =
    try {
        URI.create(spec).toURL()
    } catch (e: IllegalArgumentException) {
        // Deliberately re-thrown as the type the caller already handles. Losing the cause here
        // would lose the only description of what was wrong with the string.
        throw MalformedURLException(e.message ?: "not a URL: $spec")
    }

/**
 * Cleans a base address as a human typed it, into something safe to concatenate a path onto.
 *
 * Split out of the two clients rather than repeated in each, for the reason the whole `xo/`
 * package exists: two copies of a normalisation rule are two places to drift. It is also the
 * only way to test the rule without widening a client's public surface for a test's benefit.
 *
 * **`trim()` BEFORE `trimEnd('/')`, and the order is load bearing.** `trimEnd('/')` strips
 * slashes only, so a base typed with a trailing space keeps it, and the space then lands in the
 * MIDDLE of the request spec where it can no longer be trimmed away. Doing it the other way
 * round leaves `https://host/ ` as `https://host/ `, which is exactly the input this exists for.
 */
internal fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/').trimEnd()
