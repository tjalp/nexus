package net.tjalp.nexus.serializer

import kotlinx.datetime.TimeZone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TimeZoneIdSerializer : KSerializer<TimeZone> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TimeZone", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TimeZone) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder): TimeZone = TimeZone.of(decoder.decodeString())
}