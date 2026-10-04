package com.myremote.app.hid

import org.junit.Assert.*
import org.junit.Test

class HidAssociationTest {
    @Test fun explicitRequestBondsOnlyOnceAndConnectsOnlyAfterBondCallback() {
        var state = HidBondState.NONE; var bonds = 0; var connects = 0
        val association = HidAssociation({ state }, { bonds++; true }, { connects++ }, { fail("Rejected") })
        assertEquals(0, bonds)
        association.request(); association.request()
        assertEquals(1, bonds); assertEquals(0, connects)
        state = HidBondState.BONDING
        association.changed(HidBondState.NONE, state); association.request()
        assertEquals(1, bonds); assertEquals(0, connects)
        state = HidBondState.BONDED
        association.changed(HidBondState.BONDING, state)
        association.changed(HidBondState.BONDING, state)
        assertEquals(1, connects)
    }
    @Test fun savedBondConnectsWithoutAnotherPairingPrompt() {
        var connects = 0
        val association = HidAssociation({ HidBondState.BONDED }, { fail("Re-pair"); false }, { connects++ }, { fail("Rejected") })
        association.request(); association.request(); assertEquals(1, connects)
    }
    @Test fun cancellationAndImmediateFailureDoNotConnectOrRetryAutomatically() {
        var creates = 0; var failures = 0
        val association = HidAssociation({ HidBondState.NONE }, { ++creates == 1 }, { fail("Connected") }, { failures++ })
        association.request()
        association.changed(HidBondState.BONDING, HidBondState.NONE)
        assertEquals(1, creates); assertEquals(1, failures)
        association.request()
        assertEquals(2, creates); assertEquals(2, failures)
    }
    @Test fun disconnectAllowsAProfileReconnectWithoutAnotherBondRequest() {
        var connects = 0
        val association = HidAssociation({ HidBondState.BONDED }, { fail("Re-pair"); false }, { connects++ }, { fail("Rejected") })
        association.request(); association.request(); assertEquals(1, connects)
        association.disconnected(); association.request(); association.request()
        assertEquals(2, connects)
    }

}
