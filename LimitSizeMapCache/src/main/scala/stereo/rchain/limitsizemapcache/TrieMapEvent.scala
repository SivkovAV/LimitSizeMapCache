package stereo.rchain.limitsizemapcache

sealed trait TrieMapEvent {
  val key: Array[Byte]
}

case class GetValue(override val key: Array[Byte]) extends TrieMapEvent

case class SetValue(override val key: Array[Byte], value: Int) extends TrieMapEvent
