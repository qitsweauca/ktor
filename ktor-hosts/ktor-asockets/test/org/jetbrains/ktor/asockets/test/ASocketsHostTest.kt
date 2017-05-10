package org.jetbrains.ktor.asockets.test

import org.jetbrains.ktor.asockets.*
import org.jetbrains.ktor.testing.*
import org.junit.*

class ASocketsHostTest : HostTestSuite<CApplicationHost>(CApplicationHost.Factory, ssl = false) {

    @Test
    @Ignore
    override fun testMultipartFileUpload() {
        super.testMultipartFileUpload()
    }
}