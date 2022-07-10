package stereo.rchain.limitsizemapcache

import cats.Parallel
import cats.effect.Sync
import cats.syntax.all._
import stereo.rchain.limitsizemapcache.PerformanceComparison.ExperimentParameters
import stereo.rchain.limitsizemapcache.cacheImplamentations.{ImperativeLimitSizeMapCache, LimitSizeMapCache}

import scala.collection.concurrent.TrieMap

object CachesAggregator {
  def apply() = new CachesAggregator()
}

class CachesAggregator {

  class ImperativeTestCache[F[_]: Sync: Parallel](val maxItemCount: Int, val itemCountAfterSizeCorrection: Int)
    extends AbstractTestCache[F] {
    override val name: String = s"""ImperativeLimitSizeMapCache(${maxItemCount};${itemCountAfterSizeCorrection})"""
    private val cache = new ImperativeLimitSizeMapCache[Array[Byte], Int](maxItemCount, itemCountAfterSizeCorrection)

    override def get(key: Array[Byte]): F[Option[Int]] = Sync[F].delay(cache.get(key))

    override def set(key: Array[Byte], value: Int): F[Unit] = Sync[F].delay(cache.set(key, value))
  }

  class TrieMapTestCache[F[_]: Sync: Parallel] extends AbstractTestCache[F] {
    override val name: String = "TrieMapCache"
    private val cache = new TrieMap[Array[Byte], Int]

    override def get(key: Array[Byte]): F[Option[Int]] = Sync[F].delay(cache.get(key))

    override def set(key: Array[Byte], value: Int): F[Unit] = Sync[F].delay((cache(key) = value))
  }

  class LimitSizeTestCache[F[_]: Sync: Parallel](val maxItemCount: Int, val itemCountAfterSizeCorrection: Int)
    extends AbstractTestCache[F] {
    override val name: String = s"""LimitSizeMapCache(${maxItemCount};${itemCountAfterSizeCorrection})"""
    private val cacheRef = LimitSizeMapCache[F, Array[Byte], Int](maxItemCount, itemCountAfterSizeCorrection)

    override def get(key: Array[Byte]): F[Option[Int]] =
      for { cache <- cacheRef; value <- cache.get(key) } yield value

    override def set(key: Array[Byte], value: Int): F[Unit] =
      for { cache <- cacheRef; _ <- cache.set(key, value) } yield ()
  }

  class UnlimitedLimitSizeTestCache[F[_]: Sync: Parallel](val maxItemCount: Int) extends AbstractTestCache[F] {
    private val pseudoUnlimitedSize: Int = maxItemCount * maxItemCount
    override val name: String = "UnlimitedLimitSizeMapCache"
    private val cacheRef = LimitSizeMapCache[F, Array[Byte], Int](pseudoUnlimitedSize, pseudoUnlimitedSize)

    override def get(key: Array[Byte]): F[Option[Int]] =
      for { cache <- cacheRef; value <- cache.get(key) } yield value

    override def set(key: Array[Byte], value: Int): F[Unit] =
      for { cache <- cacheRef; _ <- cache.set(key, value) } yield ()
  }

  def prepareCaches[F[_]: Sync: Parallel](
    maxItemCount: Int,
    itemCountAfterSizeCorrection: Int
  ): List[AbstractTestCache[F]] = {
    List(
      new TrieMapTestCache,
      new ImperativeTestCache(maxItemCount, itemCountAfterSizeCorrection),
      new LimitSizeTestCache[F](maxItemCount, itemCountAfterSizeCorrection),
      new UnlimitedLimitSizeTestCache[F](maxItemCount)
    )
  }

  def cachesNames[F[_]: Sync: Parallel](params: ExperimentParameters): List[String] =
    prepareCaches(params.maxItemCount, params.itemCountAfterSizeCorrection).map(_.name)
}
