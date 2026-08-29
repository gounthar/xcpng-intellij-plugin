package dev.gounthar.xcpng.ide

import dev.gounthar.xcpng.ide.xo.XoClient
import dev.gounthar.xcpng.ide.xo.XoPowerState
import dev.gounthar.xcpng.ide.xo.XoVm

/**
 * What a tool window renders, and what it is allowed to offer, with no IntelliJ imports.
 *
 * This is the seam between Xen Orchestra and the IDE, and it holds the split that made the
 * toolbox sibling worth reusing at all: its `xo/` package is 1121 lines with zero JetBrains
 * imports, so it ports to any JVM host, while its 1745-line shell is welded to one. Everything
 * platform-facing goes above this file, everything platform-independent below it.
 *
 * The rule: if a class under `xo/` ever needs a `com.intellij` import, the design has gone
 * wrong and the fix belongs here, not there.
 *
 * ## Why this is a DIFFERENT problem from the gateway sibling's
 *
 * That repository asks one question: can the IDE be pointed at this VM, and if not, why. It is
 * a connector, so it hands over a machine and an endpoint and stops.
 *
 * This one is inside a running IDE and never stops. Its question is what a person can see and
 * do without leaving the editor. So the decisions here are about presentation and about which
 * actions are offered, which is why [Availability] models a REASON rather than a boolean:
 * a greyed-out menu item that does not say why is the thing users file bugs about.
 *
 * Google's two plugins split the same way, which is the clearest available evidence the line
 * is real rather than an artifact of how this was built. `Cloud Code` is the in-IDE
 * integration; `Cloud Workstations`, a separate plugin whose descriptor is literally named
 * `...jetbrains.connector`, is the launcher half.
 */
class XoResourceModel(private val client: XoClient) {

    /** Why an action is not offered on a VM, or [OFFERED] when it is. */
    enum class Availability {
        OFFERED,

        /** The VM is already in the state the action would produce. */
        ALREADY_THERE,

        /** The action needs the VM running and it is not. */
        NEEDS_RUNNING,

        /**
         * The action asks the guest to do something and the guest is not reporting, so the
         * request would be accepted and then never happen.
         *
         * This is the one worth having as its own value. On the lab pool only 1 of 4 running
         * VMs was reporting at all, so it is the common case rather than the exotic one, and
         * a clean shutdown offered on such a VM looks like a plugin that does nothing.
         */
        NEEDS_GUEST_AGENT,
    }

    /**
     * The actions a tool window can offer on a VM.
     *
     * Named for what the user wants, not for the XO verb. The verb depends on the current
     * state, and that mapping has exactly one implementation, in `xo/`.
     */
    enum class Action { POWER_ON, CLEAN_SHUTDOWN, HARD_SHUTDOWN, SNAPSHOT, REVERT }

    /** Whether [action] should be offered on [vm], and why not when it should not. */
    fun availability(vm: XoVm, action: Action): Availability = when (action) {
        Action.POWER_ON ->
            if (vm.powerState == XoPowerState.RUNNING) Availability.ALREADY_THERE
            else Availability.OFFERED

        // Asks the guest. Needs the VM running AND the guest reporting.
        Action.CLEAN_SHUTDOWN -> when {
            vm.powerState != XoPowerState.RUNNING -> Availability.NEEDS_RUNNING
            vm.guestIsReporting == false -> Availability.NEEDS_GUEST_AGENT
            else -> Availability.OFFERED
        }

        // Does not ask the guest, which is the whole reason it exists beside the clean one.
        Action.HARD_SHUTDOWN ->
            if (vm.powerState == XoPowerState.RUNNING) Availability.OFFERED
            else Availability.NEEDS_RUNNING

        // Both work on a halted VM. XO snapshots and reverts regardless of power state, and
        // reverting a running VM is a normal thing to want.
        Action.SNAPSHOT, Action.REVERT -> Availability.OFFERED
    }

    /**
     * How a VM's address should be presented, which is not the same as whether it exists.
     *
     * Four outcomes, and they need four different lines on screen. "No address" is not one
     * fact: not running, no agent so no address is ever coming, and still booting are three
     * situations a user acts on differently, and collapsing them is what produces a tool
     * window that shows a blank column and teaches nobody anything.
     *
     * **Power state is checked FIRST, and the order is load bearing.** The first version of
     * this method checked the address first and returned a stale one for a halted VM. XO
     * really does serve a last known address for a stopped VM, and on the lab pool 4 of 6
     * halted VMs reported one against 1 of 4 running ones, so this is the common case rather
     * than a corner. `XoRestClient` drops the field for a non-running VM, which means the
     * ordering here is a second line of defence rather than the only one. It is kept anyway,
     * because this method takes an [XoVm] from any caller and must not depend on where that
     * value was built.
     */
    fun addressLabel(vm: XoVm): String {
        if (vm.powerState != XoPowerState.RUNNING) return "not running"
        return vm.mainIpAddress
            ?: if (vm.guestIsReporting == false) "no guest agent" else "waiting for an address"
    }

    /** Every VM on the pool, as XO reports it. */
    suspend fun vms(): List<XoVm> = client.listVms()

    /**
     * One VM, re-read from the pool.
     *
     * Always re-read after acting, never assume the action did what it said. The sibling
     * records that the events stream serves the raw XO object rather than the REST view, so
     * the two disagree on shape and only one is what this code expects.
     */
    suspend fun refresh(vm: XoVm): XoVm? = client.getVm(vm.uuid)
}
