package zio.mongo

import com.mongodb.async.client.{MongoClient, MongoClients, MongoCollection, MongoDatabase}
import org.bson.Document
import zio.{Task, ZManaged}


object Mongo {

  def fromUrl(url: String): ZManaged[Any, Throwable, MongoClient] =
    ZManaged.make{
      Task.effect(MongoClients.create(url))
    }{c =>
      Task.effect(c.close()).ignore
    }

  def database(c: MongoClient, d: String): Task[MongoDatabase] = Task.effect(c.getDatabase(d))

  def collection(d: MongoDatabase, c: String): Task[MongoCollection[Document]] = Task.effect(d.getCollection(c))

}
