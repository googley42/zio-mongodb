# zio-mongodb

A ZIO port of an [fs2/cats-effect MongDB streaming project](https://github.com/fiadliel/fs2-mongodb), primarily so that 
I could explore the ZIO async effect and streaming APIs. It's based on the old MongoDB async Java client 
[here](https://github.com/fiadliel/fs2-mongodb).

Note the [MongoDB async java client](https://mongodb.github.io/mongo-java-driver/) has been deprecated in favour
of the new [Reactive Streams based driver](https://mongodb.github.io/mongo-java-driver/) - so a more robust approach
would be to use the [ZIO reactive-streams interop library](https://github.com/zio/interop-reactive-streams) with 
this new driver.

## Usage

```Scala
import Mongo._
import MongoOps._

def withClient(c: MongoClient): ZIO[Console, Throwable, Long] = {
  for {
    db <- database(c, "test")
    people <- collection(db, "people")
    _ <- people.zioDeleteMany(new Document())
    _ <- people.zioInsertOne(new Document().append("id", 1).append("forename", "John").append("surname", "Smith"))
    _ <- people.zioInsertOne(new Document().append("id", 2).append("forename", "John").append("surname", "Doe"))
    count <- people.zioCount
    _ <- people.find().batchSize(10).stream.run(ZSink.foldM(0){ (count, doc: Document) =>
      console.putStrLn(doc.toString) *> UIO.succeed(ZSink.Step.more(count+1))
    })
  } yield count
}

fromUrl("mongodb://localhost").use(withClient)
```

There is an [example Scala file here](/src/main/scala/zio/mongo/ExampleUsage.scala) that you can run.

 


  
        