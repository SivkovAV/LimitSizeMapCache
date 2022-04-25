package stereo.rchain.mapcache

import cats.effect.concurrent.Ref
import monix.eval.Task
import org.scalatest.flatspec.AnyFlatSpec
import cats.syntax.all._
import cats.effect.Sync

case class MyClass[F[_]: Sync](val refValue: Ref[F, Int]) {
  def set(newValue: Int): F[Unit] = for {
    _ <- refValue.update(_ => {println("it's never printed"); newValue})
  } yield()
}


class StackSpec extends AnyFlatSpec {
  "it" should "work" in {
    for {
      refValue <- Ref.of[Task, Int](0)
      _ <- refValue.update(i => i)
    } yield()


      /*refValue.runSyncUnsafe().set(1)
      myClass = MyClass[Task](refValue)
      myClass.run
      _ <- myClass.set(1).pure*/
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
