package com.agon.app.vpn

/**
 * Minimal DNS/IPv4/UDP codec used by [FamilyVpnService].
 *
 * Extracted from the service so the packet loop stays allocation free and the wire format logic
 * is testable in isolation. All helpers operate on caller supplied buffers.
 */
internal object DnsPacket {

    const val IPV4 = 4
    const val PROTO_UDP = 17
    const val DNS_PORT = 53
    const val MIN_HEADER = 20

    /** DNS response codes. */
    const val RCODE_NO_ERROR = 0
    const val RCODE_NXDOMAIN = 3

    fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    fun put32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    /**
     * Parses the QNAME of the first question. Handles the 63 byte label limit and refuses
     * compression pointers, which are illegal in a query section.
     */
    fun parseDomain(query: ByteArray, length: Int): String {
        if (length < 13) return ""
        val builder = StringBuilder(48)
        var index = 12
        var labels = 0
        while (index < length) {
            val labelLength = query[index].toInt() and 0xFF
            if (labelLength == 0) break
            if (labelLength and 0xC0 != 0) return "" // compression pointer, not valid in a query
            if (labelLength > 63 || index + labelLength >= length) return ""
            if (builder.isNotEmpty()) builder.append('.')
            builder.append(String(query, index + 1, labelLength, Charsets.UTF_8))
            index += labelLength + 1
            if (++labels > 32) return ""
        }
        return builder.toString()
    }

    /** Offset just past the QNAME, or -1 when malformed. */
    private fun questionEnd(query: ByteArray, length: Int): Int {
        var index = 12
        while (index < length) {
            val labelLength = query[index].toInt() and 0xFF
            if (labelLength == 0) return index + 1
            if (labelLength and 0xC0 != 0) return -1
            if (labelLength > 63 || index + labelLength >= length) return -1
            index += labelLength + 1
        }
        return -1
    }

    /** QTYPE of the first question, or -1 when the packet is malformed. */
    fun questionType(query: ByteArray, length: Int): Int {
        val end = questionEnd(query, length)
        if (end < 0 || end + 4 > length) return -1
        return u16(query, end)
    }

    fun responseCode(response: ByteArray, length: Int): Int =
        if (length < 12) -1 else response[3].toInt() and 0x0F

    /**
     * Builds a sinkhole answer.
     *
     * A plain NXDOMAIN is what the previous implementation returned, and it is exactly what
     * lets blocked sites reappear: browsers and the platform resolver treat NXDOMAIN as a
     * transient failure, retry over IPv6, fall back to a secondary resolver and keep serving
     * the cached page. Instead we return an authoritative A/AAAA answer pointing at a dead
     * address with TTL 0, so nothing is ever cached and every retry is answered the same way.
     */
    fun buildSinkholeResponse(query: ByteArray, length: Int, out: ByteArray): Int {
        val end = questionEnd(query, length)
        if (end < 0 || end + 4 > length) return buildRefused(query, length, out)
        val qtype = u16(query, end)
        val questionLength = end + 4
        if (questionLength > out.size) return buildRefused(query, length, out)

        System.arraycopy(query, 0, out, 0, questionLength)
        // QR=1, Opcode copied, AA=1, RD copied, RA=1, RCODE=0
        out[2] = ((query[2].toInt() and 0x01) or 0x84).toByte()
        out[3] = 0x80.toByte()
        put16(out, 4, 1) // QDCOUNT
        put16(out, 6, if (qtype == TYPE_A || qtype == TYPE_AAAA) 1 else 0) // ANCOUNT
        put16(out, 8, 0) // NSCOUNT
        put16(out, 10, 0) // ARCOUNT

        var offset = questionLength
        when (qtype) {
            TYPE_A -> {
                if (offset + 16 > out.size) return buildRefused(query, length, out)
                offset = writeAnswerHeader(out, offset, TYPE_A, 4)
                // 0.0.0.0 - unroutable, connection fails immediately instead of hanging.
                out[offset] = 0; out[offset + 1] = 0; out[offset + 2] = 0; out[offset + 3] = 0
                offset += 4
            }
            TYPE_AAAA -> {
                if (offset + 28 > out.size) return buildRefused(query, length, out)
                offset = writeAnswerHeader(out, offset, TYPE_AAAA, 16)
                java.util.Arrays.fill(out, offset, offset + 16, 0)
                offset += 16
            }
        }
        return offset
    }

    private fun writeAnswerHeader(out: ByteArray, start: Int, type: Int, dataLength: Int): Int {
        var offset = start
        // Compression pointer back to the QNAME at offset 12.
        out[offset] = 0xC0.toByte(); out[offset + 1] = 0x0C
        offset += 2
        put16(out, offset, type); offset += 2
        put16(out, offset, CLASS_IN); offset += 2
        put32(out, offset, 0); offset += 4 // TTL 0 defeats every downstream DNS cache
        put16(out, offset, dataLength); offset += 2
        return offset
    }

    /**
     * Rewrites the QNAME of a query in place, producing a query for [replacement] while keeping
     * the transaction id, flags and QTYPE untouched.
     *
     * This is how SafeSearch is enforced: the client asks for `google.com`, we ask upstream for
     * `forcesafesearch.google.com`, and the address the client receives is the provider's
     * SafeSearch endpoint. The client is unaware, so it works in every browser, in incognito and
     * after any refresh.
     *
     * Returns the new packet length, or -1 when the rewrite does not fit or the packet is
     * malformed.
     */
    fun rewriteQuestion(query: ByteArray, length: Int, replacement: String, out: ByteArray): Int {
        val end = questionEnd(query, length)
        if (end < 0 || end + 4 > length) return -1
        if (replacement.isEmpty() || replacement.length > 253) return -1

        val labels = replacement.split('.')
        var encodedLength = 1
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) return -1
            encodedLength += 1 + label.length
        }

        val trailing = length - (end + 4) // additional records such as EDNS OPT
        val total = 12 + encodedLength + 4 + trailing
        if (total > out.size) return -1

        // Header is copied verbatim so the transaction id still matches the client's request.
        System.arraycopy(query, 0, out, 0, 12)

        var offset = 12
        for (label in labels) {
            out[offset++] = label.length.toByte()
            for (index in label.indices) out[offset++] = label[index].code.toByte()
        }
        out[offset++] = 0

        // QTYPE + QCLASS preserved from the original question.
        out[offset++] = query[end]
        out[offset++] = query[end + 1]
        out[offset++] = query[end + 2]
        out[offset++] = query[end + 3]

        if (trailing > 0) {
            System.arraycopy(query, end + 4, out, offset, trailing)
            offset += trailing
        }
        return offset
    }

    /**
     * Extracts the first A or AAAA address from an answer section.
     *
     * Names inside the answer are skipped rather than parsed, because they may use compression
     * pointers. Returns the offset of the RDATA, or -1 when no matching record exists.
     */
    fun findAddressRecord(response: ByteArray, length: Int, wantedType: Int): Int {
        if (length < 12) return -1
        val answers = u16(response, 6)
        if (answers <= 0) return -1
        var offset = questionEnd(response, length)
        if (offset < 0 || offset + 4 > length) return -1
        offset += 4 // QTYPE + QCLASS

        var index = 0
        while (index < answers && offset < length) {
            offset = skipName(response, offset, length)
            if (offset < 0 || offset + 10 > length) return -1
            val type = u16(response, offset)
            val dataLength = u16(response, offset + 8)
            offset += 10
            if (offset + dataLength > length) return -1
            val expected = if (wantedType == TYPE_AAAA) 16 else 4
            if (type == wantedType && dataLength == expected) return offset
            offset += dataLength
            index++
        }
        return -1
    }

    /** Advances past a (possibly compressed) domain name. Returns -1 when malformed. */
    private fun skipName(data: ByteArray, start: Int, length: Int): Int {
        var offset = start
        var guard = 0
        while (offset < length) {
            val label = data[offset].toInt() and 0xFF
            if (label == 0) return offset + 1
            if (label and 0xC0 == 0xC0) return offset + 2 // compression pointer terminates the name
            offset += label + 1
            if (++guard > 128) return -1
        }
        return -1
    }

    /**
     * Builds an authoritative answer for the client's *original* question carrying [address].
     *
     * This is how the SafeSearch result is delivered: the upstream lookup is performed for the
     * provider's SafeSearch hostname, and the address it returns is handed back under the name
     * the client actually asked for. Building a fresh packet avoids any compression-pointer
     * rewriting, so the reply is always well formed.
     *
     * TTL is deliberately short so a policy change takes effect quickly, and the record can
     * never outlive a rule update by more than [SAFE_SEARCH_TTL] seconds.
     */
    fun buildAddressResponse(
        query: ByteArray,
        queryLength: Int,
        address: ByteArray,
        addressOffset: Int,
        addressLength: Int,
        out: ByteArray,
    ): Int {
        val end = questionEnd(query, queryLength)
        if (end < 0 || end + 4 > queryLength) return -1
        val type = u16(query, end)
        if (addressLength != 4 && addressLength != 16) return -1
        if (addressOffset + addressLength > address.size) return -1

        val questionLength = end + 4
        val total = questionLength + 12 + addressLength
        if (total > out.size) return -1

        System.arraycopy(query, 0, out, 0, questionLength)
        out[2] = ((query[2].toInt() and 0x01) or 0x84).toByte() // QR + AA + RD
        out[3] = 0x80.toByte() // RA, RCODE 0
        put16(out, 4, 1)
        put16(out, 6, 1)
        put16(out, 8, 0)
        put16(out, 10, 0)

        var offset = questionLength
        out[offset] = 0xC0.toByte(); out[offset + 1] = 0x0C
        offset += 2
        put16(out, offset, type); offset += 2
        put16(out, offset, CLASS_IN); offset += 2
        put32(out, offset, SAFE_SEARCH_TTL); offset += 4
        put16(out, offset, addressLength); offset += 2
        System.arraycopy(address, addressOffset, out, offset, addressLength)
        return offset + addressLength
    }

    /** Short TTL so SafeSearch answers cannot be cached across a rule change. */
    const val SAFE_SEARCH_TTL = 60

    /** REFUSED reply for malformed or unsupported queries. */
    private fun buildRefused(query: ByteArray, length: Int, out: ByteArray): Int {
        val size = minOf(length, out.size, 12)
        if (size < 12) return 0
        System.arraycopy(query, 0, out, 0, size)
        out[2] = 0x81.toByte()
        out[3] = 0x85.toByte() // RA + RCODE 5 (REFUSED)
        put16(out, 4, u16(query, 4))
        put16(out, 6, 0); put16(out, 8, 0); put16(out, 10, 0)
        return size
    }

    /**
     * Wraps [dns] in IPv4+UDP, swapping source and destination from the original [request].
     * Returns the total packet length written into [out].
     */
    fun wrapUdp(request: ByteArray, requestHeaderLength: Int, dns: ByteArray, dnsLength: Int, out: ByteArray): Int {
        val total = MIN_HEADER + 8 + dnsLength
        if (total > out.size) return 0

        out[0] = 0x45
        out[1] = 0
        put16(out, 2, total)
        put16(out, 4, u16(request, 4)) // identification
        put16(out, 6, 0x4000) // don't fragment
        out[8] = 64 // TTL
        out[9] = PROTO_UDP.toByte()
        out[10] = 0; out[11] = 0
        System.arraycopy(request, 16, out, 12, 4) // original dst becomes src
        System.arraycopy(request, 12, out, 16, 4) // original src becomes dst
        put16(out, 10, checksum(out, 0, MIN_HEADER))

        put16(out, 20, DNS_PORT)
        put16(out, 22, u16(request, requestHeaderLength)) // original source port
        put16(out, 24, 8 + dnsLength)
        put16(out, 26, 0) // UDP checksum optional on IPv4
        System.arraycopy(dns, 0, out, 28, dnsLength)
        return total
    }

    fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            index += 2
        }
        if (index < end) sum += (data[index].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val CLASS_IN = 1
}
