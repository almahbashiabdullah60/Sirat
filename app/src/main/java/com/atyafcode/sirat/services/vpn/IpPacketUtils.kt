package com.atyafcode.sirat.services.vpn

object IpPacketUtils {
    /**
     * Calculates the IPv4 header checksum.
     * The checksum field itself must be set to 0 before calculation.
     */
    fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        var remaining = length

        while (remaining > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            remaining -= 2
        }

        if (remaining > 0) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv() and 0xFFFF).toShort()
    }
}
