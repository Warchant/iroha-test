package iroha.mst

import iroha.protocol.Endpoint
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.Utils
import jp.co.soramitsu.iroha.testcontainers.IrohaContainer
import jp.co.soramitsu.iroha.testcontainers.PeerConfig
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
import jp.co.soramitsu.iroha.testcontainers.detail.IrohaConfig
import spock.lang.Specification

import javax.xml.bind.DatatypeConverter
import java.security.KeyPair

import static jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder.*

class Mst extends Specification {

    final def crypto = new Ed25519Sha3()

    final def sharedSignatory = crypto.generateKeypair()

    final def mst1Account = "mst1@${defaultDomainName}"
    final def mst1KeyPair = crypto.generateKeypair()

    final def mst2Account = "mst2@${defaultDomainName}"
    final def mst2KeyPair = crypto.generateKeypair()

    def gb = new GenesisBlockBuilder()
            .addDefaultTransaction()
            .addTransaction(
            Transaction.builder(defaultAccountId)
                    .createAccount(mst1Account, mst1KeyPair.getPublic())
                    .createAccount(mst2Account, mst2KeyPair.getPublic())
                    .sign(defaultKeyPair)
                    .build()
    ).build()

    def ic = IrohaConfig.builder()
            .mst_enable(true)
            .build()


    def pc = PeerConfig.builder()
            .irohaConfig(ic)
            .genesisBlock(gb)
            .build()

    def iroha = new IrohaContainer()
            .withPeerConfig(pc)

    def setup() {
        iroha.start()
    }

    def cleanup() {
        iroha.stop()
    }

    def addSharedSignatory(String accountId, KeyPair keyPair) {
        return Transaction.builder(accountId)
                .addSignatory(accountId, sharedSignatory.getPublic())
                .setAccountQuorum(accountId, 2)
                .sign(keyPair)
                .build()
    }


    def sendAndWait(IrohaAPI api, TransactionOuterClass.Transaction tx) {
        api.transactionSync(tx)
        byte[] hash = Utils.hash(tx)
        int counter = 0
        while (counter < 20) {
            def r = api.txStatusSync(hash)
            if (r.txStatus == Endpoint.TxStatus.COMMITTED) break
            println("counter: ${++counter}; \t ${r.txStatus} \t ${DatatypeConverter.printHexBinary(hash)}")
            Thread.sleep(100)
        }
    }


    def "same signatory for multiple accounts"() {
        given:
        def api = iroha.getApi()
        def tx1 = addSharedSignatory(mst1Account, mst1KeyPair)
        def tx2 = addSharedSignatory(mst2Account, mst2KeyPair)

        when:
        sendAndWait(api, tx1)
        sendAndWait(api, tx2)

        def kp = crypto.generateKeypair()
        def tx3 = Transaction.builder(mst1Account)
                .createAccount("z@${defaultDomainName}", kp.getPublic())
                .sign(mst1KeyPair)
                .sign(sharedSignatory)
                .build()

        sendAndWait(api, tx3)

        then:
        true


    }
}
