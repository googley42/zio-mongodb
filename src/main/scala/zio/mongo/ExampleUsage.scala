package zio.mongo

import com.mongodb.async.client.MongoClient
import org.bson.Document
import zio.console.Console
import zio.stream.ZSink
import zio.{App, UIO, ZIO, console}

object ExampleUsage extends App {

  override def run(args: List[String]): ZIO[Console, Nothing, Int] = {
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
      .fold(_ => 1, _ => 0)

  }
}
