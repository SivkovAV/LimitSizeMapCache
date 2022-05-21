# LimitSizeMapCache

[![Build](https://github.com/SivkovAV/LimitSizeMapCache/workflows/build/badge.svg?branch=master)](https://github.com/SivkovAV/LimitSizeMapCache/actions?query=branch%3Amaster+workflow%3Abuild)

### Overview
`LimitSizeMapCache` is associative array with a fixed maximum number of items (elements).
`LimitSizeMapCache` is designed to store the most frequently used items.
This means that the `LimitSizeMapCache` remembers how long ago each of its item was used (written or read).
It's convenient to use this as cache for any kind of associative arrays.
`LimitSizeMapCache` is thread safe.
`LimitSizeMapCache` based on `scala.collection.immutable.Map` and `cats.effect.concurrent.Ref` so it's work without locks.

Like any associative array, this array is designed to store key-value pairs of elements.
`Key` - is unique value for all items stored in `LimitSizeMapCache`.
`Value` - user value stored in `LimitSizeMapCache`.

Finally, we can think about `LimitSizeMapCache` as the `scala.collection.mutable.Map` with fixed maximum number of items.
### Declaration
To declare a `LimitSizeMapCache` instance, we must specify the `key` type, `value` type,
maximum storage size and storage size after cleaning old elements.
```
LimitSizeMapCache[F[_], KeyType, ValueType](maxItemCount: Int, itemCountAfterSizeCorrection: Int)
```
`maxItemCount` define maximum count of stored items. If we add new item in `LimitSizeMapCache` inner storage remove several old items.
As result `LimitSizeMapCache` will store only `itemCountAfterSizeCorrection` most fresh items.
Of course `itemCountAfterSizeCorrection` should be less or equal than `maxItemCount`.
`LimitSizeMapCache` provides the ability to specify different values of `maxItemCount` and `itemCountAfterSizeCorrection`
to increase speed of operation: the greater the difference in count values,
the less often items cleaning procedure will be called.

### Code example,
```
for {
  cache <- LimitSizeMapCache[F, Int, String](4, 2)  # cache: empty
  _ <- cache.set(0, "0")                            # cache: (0->"0")
  _ <- cache.set(1, "1")                            # cache: (0->"0", 1->"1")
  _ <- cache.set(2, "2")                            # cache: (0->"0", 1->"1", 2->"2")
  _ <- cache.set(3, "3")                            # cache: (0->"0", 1->"1", 2->"2", 3->"3")
  _ <- cache.set(4, "4")                            # cache: (3->"3", 4->"4")
  _ <- cache.set(5, "5")                            # cache: (3->"3", 4->"4", 5->"5")
  _ <- cache.set(6, "6")                            # cache: (3->"3", 4->"4", 5->"5", 6->"6")
  _ <- cache.set(7, "7")                            # cache: (6->"6", 7->"7")
  _ <- cache.set(8, "8")                            # cache: (6->"6", 7->"7", 8->"8")
  _ <- cache.get(9)                                 # cache: (6->"6", 7->"7", 8->"8")
  _ <- cache.get(8)                                 # cache: (6->"6", 7->"7", 8->"8")
  _ <- cache.get(6)                                 # cache: (7->"7", 8->"8", 6->"6")
  
} yield()
```

### Project structure
* All source code of `LimitSizeMapCache` is [here](https://github.com/SivkovAV/mapCache/blob/master/LimitSizeMapCache/src/main/scala/stereo/rchain/mapcache/cacheImplamentations/LimitSizeMapCache.scala)

* Unittests for `LimitSizeMapCache` is [here](https://github.com/SivkovAV/mapCache/blob/master/LimitSizeMapCache/src/test/scala/stereo/rchain/mapcache/TestForCustomCache.scala)

* other source code files from [here](https://github.com/SivkovAV/mapCache) a program that generates html files
with performance comparison graphs; these graphs allow us to compare `LimitSizeMapCache` with other storages in various tasks;
internet connection required to view these graphs
