package dev.nfcstudio.app.testing

import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.nfc.DiscoveredTag
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.TagGateway
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.core.nfc.Verification

/** Test-only handle; production code only ever sees PlatformTag. */
object TestTag : DiscoveredTag

/** Test double recording every operation; lives in test sources only (AGENTS rule 8). */
class FakeTagGateway(
    var readResult: NfcResult<TagSnapshot>,
    var writeResult: NfcResult<Verification> = NfcResult.Ok(Verification.VERIFIED),
    var formatResult: NfcResult<Verification> = NfcResult.Ok(Verification.NOT_POSSIBLE),
    var lockResult: NfcResult<Unit> = NfcResult.Ok(Unit),
) : TagGateway {
    val writes = mutableListOf<List<RecordSpec>>()
    val formats = mutableListOf<List<RecordSpec>>()
    var locks = 0

    override fun read(tag: DiscoveredTag) = readResult
    override fun write(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification> {
        writes += specs
        return writeResult
    }

    override fun formatAndWrite(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification> {
        formats += specs
        return formatResult
    }

    override fun makeReadOnly(tag: DiscoveredTag): NfcResult<Unit> {
        locks++
        return lockResult
    }
}
