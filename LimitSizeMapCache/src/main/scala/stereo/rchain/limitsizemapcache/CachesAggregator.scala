package stereo.rchain.limitsizemapcache

import cats.Parallel
import cats.effect.Sync

class CachesAgregator {
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
}
