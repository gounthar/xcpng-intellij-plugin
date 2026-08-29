package dev.gounthar.xcpng.ide.xo

import java.net.MalformedURLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [urlOf], and specifically the two ways a naive replacement of the deprecated `URL(String)`
 * constructor changes behaviour without failing the build.
 *
 * The Plugin Verifier reported the deprecation; `kotlinc` does not warn on it at all, so
 * nothing in an ordinary build would have raised it and nothing would have caught a bad fix
 * either. These tests are the substitute for that.
 */
class UrlsTest {

    @Test
    fun `an ordinary appliance URL builds`() {
        assertEquals(
            "https://xoa.example.com/rest/v0/vms",
            urlOf("https://xoa.example.com/rest/v0/vms").toString(),
        )
    }

    /**
     * The load-bearing one. `URI.create` throws `IllegalArgumentException`, which is unchecked
     * and NOT an `IOException`, so it falls through every branch of [xoUnreachableMessage] to
     * the bare `else`. Keeping the thrown type is what preserves the guided message, and the
     * next test proves the message rather than trusting this one.
     */
    @Test
    fun `a spec that is not a URL still throws MalformedURLException`() {
        // No `assertTrue(failure is IOException)` here, though the first version had one. The
        // compiler rejects it as always true, because MalformedURLException statically is an
        // IOException, so the line could never fail and was decoration wearing the costume of a
        // check. The type in assertFailsWith is the assertion; the guided-message test below is
        // what proves it still matters.
        assertFailsWith<MalformedURLException> { urlOf("not a url/rest/v0/vms") }
    }

    /**
     * `URI.toURL()` throws `IllegalArgumentException` for a URI that parses but is not
     * absolute, which is a second route to the same wrong type and is easy to miss because
     * `URI.create` itself accepts the string.
     */
    @Test
    fun `a relative URI also throws MalformedURLException rather than IllegalArgumentException`() {
        assertFailsWith<MalformedURLException> { urlOf("/rest/v0/vms") }
    }

    /**
     * The regression this exists to prevent, asserted on the message a person actually reads
     * rather than on the exception type. If someone swaps in `URI.create(...).toURL()` and
     * drops the mapping, this fails with the bare fallback text.
     */
    @Test
    fun `a bad address still produces the guided settings message`() {
        val failure = runCatching { urlOf("not a url/rest/v0/vms") }.exceptionOrNull()!!
        val message = xoUnreachableMessage("not a url", failure)
        assertTrue(
            message.contains("appliance's own address and nothing more"),
            "expected the guided message, got: $message",
        )
    }

    /**
     * Whitespace, which is the half [urlOf] deliberately does NOT fix.
     *
     * By the time a spec reaches [urlOf] the base and the path are concatenated, so a space
     * typed at the end of the base is interior and unfixable here. It is trimmed in the client
     * constructors instead. These two rows record the measured difference so that a later
     * "simplification" that moves the trim into [urlOf] fails rather than merely looking tidy.
     */
    @Test
    fun `interior whitespace is refused, which is why the trim belongs in the constructor`() {
        assertFailsWith<MalformedURLException> { urlOf("https://xoa.example.com /rest/v0/vms") }
        assertFailsWith<MalformedURLException> { urlOf(" https://xoa.example.com/rest/v0/vms") }
    }

    /**
     * The trim itself, on the shared normaliser.
     *
     * The first version of this test called the client constructor and asserted on a property
     * that does not exist, having guessed the signature instead of reading it. Testing the
     * normaliser directly is both correct and better: it needs no client, and it does not widen
     * a production API for a test's benefit.
     *
     * Four DIFFERENT shapes a person plausibly types or pastes, not four spellings of one. A
     * fixture set that only ever varies the trailing slash cannot distinguish a slash trim from
     * a whitespace trim, and the entire point here is that `trimEnd('/')` alone was not enough.
     */
    @Test
    fun `the base is trimmed of whitespace as well as trailing slashes`() {
        listOf(
            "https://xoa.example.com",
            "https://xoa.example.com/",
            "https://xoa.example.com ",
            " https://xoa.example.com/ ",
        ).forEach { typed ->
            assertEquals("https://xoa.example.com", normalizeBaseUrl(typed), "typed as [$typed]")
        }
    }

    /**
     * The ordering inside the normaliser, asserted rather than described.
     *
     * `trimEnd('/')` before `trim()` leaves this input unchanged, which is the bug the comment
     * in Urls.kt warns about. Written as its own case so that reordering those calls fails a
     * test rather than only contradicting a comment nobody rereads.
     */
    @Test
    fun `a trailing slash followed by a space is fully cleaned`() {
        assertEquals("https://xoa.example.com", normalizeBaseUrl("https://xoa.example.com/ "))
    }
}
