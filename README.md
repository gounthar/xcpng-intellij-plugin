# XCP-ng for JetBrains IDEs (unofficial)

See and drive an [XCP-ng](https://xcp-ng.org/) pool from inside the IDE, through
[Xen Orchestra](https://xen-orchestra.com/). A tool window over your VMs, with the lifecycle
actions where you are already working.

**Unofficial, personal, and unaffiliated with Vates.** Nothing here is a product, mine or my
employer's.

## Status

Early. The Xen Orchestra client is carried over from a working sibling project and is
exercised by its own tests. The tool window itself is not written yet: `plugin.xml` loads and
declares no extensions, on purpose.

See `CONTEXT.md` in a working clone for what is next. It is not tracked.

## Why this exists, and why it is not the other two

Three surfaces, and confusing them is the mistake that created this repository.

| Repository | Lives in | Answers |
|---|---|---|
| `xcpng-toolbox-plugin` | the Toolbox App | pick a VM, open an IDE on it |
| `xcpng-gateway-plugin` | the Gateway launcher | the same, for people not on Toolbox |
| `xcpng-intellij-plugin` (here) | the IDE itself | see and drive the pool without leaving the IDE |

A connector, Gateway or Toolbox, hands the IDE a machine and an SSH endpoint and then stops.
It is a machine picker in a launcher, and once the IDE opens it is no longer in the picture.
So nothing a connector does is visible while you work, which is precisely the gap this plugin
fills.

Google's own plugins split along the same line, which is the clearest available evidence that
the line is real rather than an artifact of how this was built. `Cloud Code`
(`com.google.gct.core`) is the in-IDE integration: resource tool windows, deploy, log
streaming, schema validation. `Cloud Workstations`
(`com.google.cloud.workstations.ide.jetbrains.connector`) is a separate plugin, and its own
description says it "allows you to initiate remote development sessions using JetBrains
Gateway". Two plugins, because they are two jobs.

The same holds from the other side. Red Hat's and Coder's Toolbox plugins each declare
compatibility with `TOOLBOX` and nothing else, so they are structurally invisible inside an
IDE. Checked against the Marketplace API, 2026-08-29.

## What it can and cannot do

**Can**, because it is inside the IDE: list pools and VMs, show power state and addressing,
offer start, shutdown, snapshot and revert, and eventually open a terminal or push a file the
way Cloud Code does for Compute Engine.

**Cannot**, and no plugin on any surface can: automatic remote toolchain detection and remote
debugging over an arbitrary VM. Those are the IDE's own remote backend behaviour. Cloud Code
does not do them for Compute Engine VMs either; what it debugs is containers and clusters,
which is a different topology.

Worth stating plainly, because it is the half of a common request that no third party can
deliver, whatever hypervisor is underneath.

## Which IDEs

Developed against IntelliJ IDEA Community. The plugin depends only on
`com.intellij.modules.platform`, so it is intended to load in every IDE built on the platform,
WebStorm included. That intent is how the platform is documented to work and has **not** yet
been observed here.

## Building

```
./gradlew build          # compile, test, jar
./gradlew buildPlugin    # the distributable zip
./gradlew runIde         # launch a sandbox IDE with this plugin loaded
```

JDK 21. See `CONTRIBUTING.md`.

## Licence

MIT. See `LICENSE`.
