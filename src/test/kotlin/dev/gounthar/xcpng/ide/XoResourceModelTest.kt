package dev.gounthar.xcpng.ide

import dev.gounthar.xcpng.ide.XoResourceModel.Action
import dev.gounthar.xcpng.ide.XoResourceModel.Availability
import dev.gounthar.xcpng.ide.xo.XoPowerState
import dev.gounthar.xcpng.ide.xo.XoVm
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Action availability and address presentation, which are the two pieces of judgement this
 * model holds.
 *
 * The fixtures are chosen to DISAGREE with each other rather than to agree with the code. A
 * fixture set where every row is the same shape proves nothing, and the sibling project's
 * notes record two occasions where exactly that happened: a test that only ever used one tag
 * value could not tell exact matching from substring matching, and stub modules answered about
 * hardware because that is what the author believed they were for.
 *
 * The rows that do the work here are the two running-VM-with-no-address cases, because they
 * are observationally identical in XO's `mainIpAddress` field and mean opposite things.
 */
class XoResourceModelTest {

    private val model = XoResourceModel(client = FailingClient)

    private fun vm(
        power: XoPowerState,
        address: String? = null,
        reporting: Boolean? = null,
    ) = XoVm(
        uuid = "u",
        nameLabel = "n",
        powerState = power,
        mainIpAddress = address,
        guestIsReporting = reporting,
    )

    private val runningWithAgent = vm(XoPowerState.RUNNING, address = "10.0.0.9", reporting = true)
    private val runningNoAgent = vm(XoPowerState.RUNNING, reporting = false)
    private val runningStillBooting = vm(XoPowerState.RUNNING, reporting = true)
    private val halted = vm(XoPowerState.HALTED)
    private val suspended = vm(XoPowerState.SUSPENDED)

    @Test
    fun `power on is offered on anything not already running`() {
        assertEquals(Availability.OFFERED, model.availability(halted, Action.POWER_ON))
        assertEquals(Availability.OFFERED, model.availability(suspended, Action.POWER_ON))
        assertEquals(Availability.ALREADY_THERE, model.availability(runningWithAgent, Action.POWER_ON))
    }

    @Test
    fun `clean shutdown needs the guest, hard shutdown does not`() {
        // The pair is the point. Both are offered on a healthy running VM, and they diverge
        // on the VM with no agent, which is the common case on the lab pool rather than the
        // exotic one: only 1 of 4 running VMs was reporting.
        assertEquals(Availability.OFFERED, model.availability(runningWithAgent, Action.CLEAN_SHUTDOWN))
        assertEquals(Availability.OFFERED, model.availability(runningWithAgent, Action.HARD_SHUTDOWN))

        assertEquals(Availability.NEEDS_GUEST_AGENT, model.availability(runningNoAgent, Action.CLEAN_SHUTDOWN))
        assertEquals(Availability.OFFERED, model.availability(runningNoAgent, Action.HARD_SHUTDOWN))
    }

    @Test
    fun `neither shutdown is offered on a VM that is not running`() {
        assertEquals(Availability.NEEDS_RUNNING, model.availability(halted, Action.CLEAN_SHUTDOWN))
        assertEquals(Availability.NEEDS_RUNNING, model.availability(halted, Action.HARD_SHUTDOWN))
    }

    @Test
    fun `clean shutdown is offered when XO expresses no opinion about the guest`() {
        // guestIsReporting is nullable because XO does not always say. Null must not fall into
        // the NEEDS_GUEST_AGENT branch: refusing an action on the strength of a claim the pool
        // never made is a confident wrong answer, and the user can still see it fail.
        assertEquals(
            Availability.OFFERED,
            model.availability(vm(XoPowerState.RUNNING, reporting = null), Action.CLEAN_SHUTDOWN),
        )
    }

    @Test
    fun `snapshot and revert are offered whatever the power state`() {
        listOf(halted, suspended, runningWithAgent, runningNoAgent).forEach {
            assertEquals(Availability.OFFERED, model.availability(it, Action.SNAPSHOT))
            assertEquals(Availability.OFFERED, model.availability(it, Action.REVERT))
        }
    }

    @Test
    fun `the three reasons a VM has no address read differently`() {
        assertEquals("10.0.0.9", model.addressLabel(runningWithAgent))
        assertEquals("not running", model.addressLabel(halted))
        assertEquals("no guest agent", model.addressLabel(runningNoAgent))
        assertEquals("waiting for an address", model.addressLabel(runningStillBooting))
    }

    @Test
    fun `a halted VM never shows a stale address`() {
        // This is the row that caught a real bug on the day the file was written: the first
        // version checked the address before the power state and returned the stale one.
        //
        // It is deliberately an INCONSISTENT XoVm, halted and carrying an address, which
        // XoRestClient would never produce because it drops the field for a non-running VM.
        // That is the point. This asserts addressLabel is correct on its own, rather than
        // correct only because its caller happened to clean the input, and the two are
        // indistinguishable in a test that only ever passes consistent values.
        assertEquals("not running", model.addressLabel(vm(XoPowerState.HALTED, address = "10.0.0.9")))
    }
}
