package stereo.rchain.limitsizemapcache

import cats.Parallel
import cats.effect.Sync

abstract class AbstractTestCache[F[_]: Sync: Parallel] {
  val name: String

  def get(key: Array[Byte]): F[Option[Int]]

  def set(key: Array[Byte], value: Int): F[Unit]
}
