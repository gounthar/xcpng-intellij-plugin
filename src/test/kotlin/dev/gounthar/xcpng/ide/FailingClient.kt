package dev.gounthar.xcpng.ide

import dev.gounthar.xcpng.ide.xo.XoClient
import dev.gounthar.xcpng.ide.xo.XoSnapshot
import dev.gounthar.xcpng.ide.xo.XoVm

/**
 * An [XoClient] that refuses every call.
 *
 * Deliberately not a stub returning empty lists and nulls. A test double that answers
 * plausibly lets a test pass while exercising a code path nobody meant to run, which is the
 * failure mode this project's sibling has hit repeatedly and written down: the fixture agrees
 * with the belief being tested. Throwing means any accidental call is a loud test failure
 * naming the method, rather than a green run over a silent no-op.
 *
 * Every test that uses this is testing pure decision logic. The moment one legitimately needs
 * a client, write a recording double for that test rather than teaching this one to answer.
 */
object FailingClient : XoClient {
    private fun refuse(name: String): Nothing =
        error("FailingClient.$name was called; this test was not supposed to reach the pool")

    override fun ping() = refuse("ping")
    override suspend fun listVms(): List<XoVm> = refuse("listVms")
    override suspend fun getVm(uuid: String): XoVm? = refuse("getVm")
    override suspend fun start(vm: XoVm) = refuse("start")
    override suspend fun resume(vm: XoVm) = refuse("resume")
    override suspend fun unpause(vm: XoVm) = refuse("unpause")
    override suspend fun cleanShutdown(vm: XoVm) = refuse("cleanShutdown")
    override suspend fun hardShutdown(vm: XoVm) = refuse("hardShutdown")
    override suspend fun snapshot(vm: XoVm, nameLabel: String): String? = refuse("snapshot")
    override suspend fun listSnapshots(vm: XoVm): List<XoSnapshot> = refuse("listSnapshots")
    override suspend fun revertSnapshot(vm: XoVm, snapshotId: String) = refuse("revertSnapshot")
    override suspend fun primaryIpAddress(vm: XoVm): String? = refuse("primaryIpAddress")
    override fun close() = refuse("close")
}
