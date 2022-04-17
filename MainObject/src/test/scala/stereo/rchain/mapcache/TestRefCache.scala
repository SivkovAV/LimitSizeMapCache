package stereo.rchain.mapcache

import cats.effect.concurrent.Ref
import monix.eval.Task
import org.scalatest.flatspec.AnyFlatSpec
import cats.syntax.all._
import cats.effect.Sync

final case class MyClass[F[_]: Sync](val refValue: Ref[F, Int]) {
  def set(newValue: Int): F[Unit] = for {
    _ <- refValue.update(_ => {println("it's never printed"); newValue})
  } yield()
}


class StackSpec extends AnyFlatSpec {
  "it" should "work" in {
    for {
      refValue <- Ref[Task].of(0)
      myClass = MyClass[Task](refValue)
      _ <- myClass.set(1).pure
    } yield()
  }

  /*"TrieMapCacheRef" should "work without exceptions" in {
    val cache = new TrieMapCacheRef[Task, Int, Array[Byte]](1000)
    val key = Array[Byte]()
    //val value = cache.get(key)
    cache.set(key, 0)
    val value = cache.get(key)

    assert(value === None)
  }*/
}
