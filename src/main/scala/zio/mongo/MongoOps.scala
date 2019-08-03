package zio.mongo

import com.mongodb.async.client.{MongoCollection, MongoIterable}
import com.mongodb.async.{AsyncBatchCursor, SingleResultCallback}
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.WriteModel
import com.mongodb.client.result.{DeleteResult, UpdateResult}
import org.bson.Document
import zio.stream.{Stream, ZStream}
import zio.{Chunk, Task, UIO, ZIO}

import scala.collection.JavaConverters._

object MongoOps {

  private[mongo] implicit class AsyncToMongoOpt[A](val cb: Task[Option[A]] => Unit) extends AnyVal {
    def toMongo: SingleResultCallback[A] = toMongo(identity)

    def toMongo[B](f: B => A): SingleResultCallback[B] = {
      new SingleResultCallback[B] {
        override def onResult(result: B, throwable: Throwable): Unit = {
          (Option(result), Option(throwable)) match {
            case (_, Some(t)) => cb(Task.fail(t))
            case (r, None)    => cb(Task.effect(r map f))
          }
        }
      }
    }
  }

  implicit class AsyncToMongo[A](val cb: Task[A] => Unit) extends AnyVal {
    def toMongo: SingleResultCallback[A] = toMongo(identity)

    def toMongo[B](f: B => A): SingleResultCallback[B] = {
      new SingleResultCallback[B] {
        override def onResult(result: B, throwable: Throwable): Unit = {
          (result, Option(throwable)) match {
            case (_, Some(t)) => cb(Task.fail(t))
            case (r, None)    => cb(Task.effect(f(r)))
          }
        }
      }
    }
  }

  implicit class MongoIterableSyntax[A, B](iterable: A)(implicit ev: A <:< MongoIterable[B]) {
    private def asyncNext[T](cursor: AsyncBatchCursor[T]): Task[Option[Seq[T]]] = {
      if (cursor.isClosed) {
        UIO.effectTotal(None)
      } else {
        Task.effectAsync{ cb =>
          cursor.next(cb.toMongo(_.asScala))
        }
      }
    }

    private def closeCursor(maybeCursor: Option[AsyncBatchCursor[_]]): Task[Unit] =
      maybeCursor.fold[Task[Unit]](ZIO.unit)(cursor => Task.effect(cursor.close()))

    private def iterate(maybeCursor: Option[AsyncBatchCursor[B]]): ZStream[Any, Throwable, B] = {
      maybeCursor match {
        case None =>
          Stream.empty
        case Some(cursor) =>
          Stream
            .repeatEffect(asyncNext(cursor))
            .takeWhile(_.isDefined)
            .map(_.get)
            .flatMap(values =>
              Stream.fromChunk(Chunk.fromIterable(values))
            )
      }
    }

    private def asyncBatchCursor: Task[Option[AsyncBatchCursor[B]]] = {
      Task.effectAsync { cb =>
        ev(iterable).batchCursor(cb.toMongo)
      }
    }

    def stream: ZStream[Any, Throwable, B] = {
      Stream.bracket(asyncBatchCursor)(closeCursor(_).ignore).flatMap(iterate)
    }
  }


  implicit class MongoCollectionEffects[A](val underlying: MongoCollection[A]) extends AnyVal {

    def zioBulkWrite(requests: List[WriteModel[A]]): Task[BulkWriteResult] =
      Task.effectAsync{ cb =>
        underlying.bulkWrite(requests.asJava, cb.toMongo)
      }

    def zioCount: Task[Long] = {
      Task.effectAsync[Any, Throwable, java.lang.Long] { cb =>
        underlying.countDocuments(cb.toMongo)
      }.map(_.longValue())
    }

    def zioCount(filter: Document): Task[Long] = {
      Task.effectAsync[Any, Throwable, java.lang.Long] { cb =>
        underlying.countDocuments(filter, cb.toMongo)
      }.map(_.longValue())
    }

    def zioInsertOne(document: A): Task[Unit] = {
      Task.effectAsync[Any, Throwable, Void] { cb =>
        underlying.insertOne(document, cb.toMongo)
      }.unit
    }

    def zioInsertMany(documents: Seq[A]): Task[Unit] = {
      Task.effectAsync[Any, Throwable, Void] { cb =>
        underlying.insertMany(documents.asJava, cb.toMongo)
      }.unit
    }

    def zioUpdateOne(filter: Document, update: Document): Task[UpdateResult] = {
      Task.effectAsync[Any, Throwable, UpdateResult] { cb =>
        underlying.updateOne(filter, update, cb.toMongo)
      }
    }

    def zioUpdateMany(filter: Document, update: Document): Task[UpdateResult] = {
      Task.effectAsync[Any, Throwable, UpdateResult] { cb =>
        underlying.updateMany(filter, update, cb.toMongo)
      }
    }

    def zioDeleteMany(filter: Document): Task[DeleteResult] = {
      Task.effectAsync[Any, Throwable, DeleteResult] { cb =>
        underlying.deleteMany(filter, cb.toMongo)
      }
    }
  }

}


