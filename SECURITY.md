# Security

Unofficial and personal. There is no vendor behind this and no response-time commitment.

## Reporting something

Open a [private security advisory](https://github.com/gounthar/xcpng-intellij-plugin/security/advisories/new)
rather than a public issue. If that is not available to you, say that a security issue exists
and ask for a private channel, without the detail.

## What the plugin holds

A Xen Orchestra API token, and the address of the appliance it belongs to.

**Where that token is stored is NOT yet established for this project.** The sibling toolbox
plugin measured its host's secret store and recorded the backing implementation. No equivalent
measurement has been made against the IntelliJ Platform here, so nothing on this page claims
one. Establishing it is the first security task, and until it is done, treat the token as if it
were stored in the clear.

The platform does provide a credential store intended for this, and using it is the expected
answer. "Expected" is not "verified": what it falls back to when no system keychain is
available differs by operating system, and that fallback is the case worth reading rather than
the happy path.

## What the token can do, which is the part worth reading

A Xen Orchestra token is not scoped to this plugin. By default it carries the permissions of
the account that minted it, and on many installations that is an administrator: enough to
list, start, stop, snapshot and destroy every VM on the pool, not only the one you meant to
open an IDE against.

So mint a dedicated one, for a dedicated user, with the narrowest role that still lists the
VMs you need. Xen Orchestra's ACL system can scope what a user sees. Whether it is reachable
on a given appliance depends on the licence tier and, on a free appliance, on whether a trial
is running.

## The self-signed certificate question

A stock Xen Orchestra appliance serves a self-signed certificate. The vendored client carries
a trust-everything context for that case, and it is a real risk rather than a formality:
anything that can intercept the connection can serve its own certificate and take the token.
The token is the whole prize here: it is not scoped to this plugin, so on a typical
installation it carries enough to destroy every VM on the pool.

Prefer pointing the client at the appliance's CA over disabling verification.

## What reaches the log

Not yet audited for this project. The token must never be logged, and neither should a full
VM address alongside a hostname: together they describe how to reach a developer's machine,
and `idea.log` is an ordinary file on disk that people routinely attach to bug reports without
reading it first. That last habit is why this matters more in an IDE than in a launcher.

Treat "not yet audited" as "assume it leaks" until somebody reads the log with a real token
configured.

## Scope

In scope: this plugin's handling of the token, what it renders, and what it writes to disk or
to a log.

Out of scope: Xen Orchestra, XCP-ng, the JetBrains IDEs themselves, and anything reachable only
by someone who already has the machine.
