package com.anmig.autodetect.utils

import java.nio.charset.Charset

class XorDecoder {
//    fun decodeStringToString(encryptedString: String, key: String): String {
//        val bytes = encryptedString.toByteArray(Charset.forName("ISO-8859-1")).map { it.toInt() and 0xFF }
//        val decodedBytes = bytes.mapIndexed { i, b ->
//            b xor key[i % key.length].code
//        }
//        return decodedBytes.map { it.toByte() }.toByteArray().toString(Charset.forName("UTF-8"))
//    }
//
//    fun encodeStringToString(input: String, key: String): String {
//        val inputBytes = input.toByteArray(Charset.forName("UTF-8"))
//        val encryptedBytes = inputBytes.mapIndexed { i, b ->
//            (b.toInt() and 0xFF) xor key[i % key.length].code
//        }
//        return encryptedBytes.map { it.toByte() }.toByteArray().toString(Charset.forName("ISO-8859-1"))
//    }

    fun decode(bytes: List<Int>, key: String): String {
        val decodedBytes = bytes.mapIndexed { i, b ->
            b xor key[i % key.length].code
        }
        return decodedBytes.map { it.toByte() }.toByteArray().toString(Charset.forName("UTF-8"))
    }

    fun encode(input: String, key: String): List<Int> {
        val inputBytes = input.toByteArray(Charset.forName("UTF-8"))
        return inputBytes.mapIndexed { i, b ->
            b.toInt() xor key[i % key.length].code
        }
    }
}