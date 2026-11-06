package com.dxn.stockpoint.data.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class BooleanTypeAdapter : TypeAdapter<Boolean>() {
    override fun write(out: JsonWriter, value: Boolean?) {
        out.value(value)
    }

    override fun read(inp: JsonReader): Boolean {
        return when (inp.peek()) {
            JsonToken.BOOLEAN -> inp.nextBoolean()
            JsonToken.NUMBER -> inp.nextLong() != 0L
            JsonToken.STRING -> {
                val s = inp.nextString().trim()
                s.equals("true", ignoreCase = true) || s == "1" || s.equals("yes", ignoreCase = true)
            }
            JsonToken.NULL -> {
                inp.nextNull()
                false
            }
            else -> {
                inp.skipValue()
                false
            }
        }
    }
}
