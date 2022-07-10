package stereo.rchain.limitsizemapcache

import cats.Parallel
import cats.effect.Sync
import cats.syntax.all._

import java.security.MessageDigest

object TrieMapEventUtils {
  def prepareEventTask[F[_]: Sync: Parallel](cache: AbstractTestCache[F], event: TrieMapEvent): F[Unit] =
    event match {
      case GetValue(key) => cache.get(key).as(())
      case SetValue(key, value) => cache.set(key, value)
    }

  def sha256(value: Int): Array[Byte] =
    MessageDigest.getInstance("sha-256").digest(BigInt(value).toByteArray)

  def prepareGetEvents(uniqueCount: Int, repeatCount: Int): List[TrieMapEvent] = {
    val uniqueEventList = (0 until uniqueCount).toList.map(i => GetValue(sha256(i)))
    List.fill(repeatCount)(uniqueEventList).flatten
  }

  def prepareSetEvents(uniqueCount: Int, repeatCount: Int): List[TrieMapEvent] = {
    val uniqueEventList = (0 until uniqueCount).toList.map(i => SetValue(sha256(i), i))
    List.fill(repeatCount)(uniqueEventList).flatten
  }
}
