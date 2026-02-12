package net.tjalp.nexus.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

object LocaleAsStringSerializer : KSerializer<Locale> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("net.tjalp.nexus.serializer.LocaleAsStringSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Locale) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Locale = Locale.of(decoder.decodeString())
}