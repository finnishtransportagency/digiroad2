package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import net.spy.memcached.transcoders.SerializingTranscoder
import net.spy.memcached.{AddrUtil, ConnectionFactory, ConnectionFactoryBuilder, MemcachedClient}
import org.slf4j.{Logger, LoggerFactory}


case class CachedValue(data: Any, success: Boolean)

class CacheClient {
  val logger: Logger = LoggerFactory.getLogger(getClass)
  //in second
  val defaultTTL: Int = Digiroad2Properties.cacheTTL.toInt
  // Remember also check memcache max_item_size configuration.
  lazy val transcoder = new SerializingTranscoder(128 * 1024 * 1024) 
  lazy val connectionFactory: ConnectionFactory = new ConnectionFactoryBuilder()
    .setOpTimeout(1000000L) 
    .setTranscoder(transcoder).build()

  lazy val client = new MemcachedClient(connectionFactory,
    AddrUtil.getAddresses(
      Digiroad2Properties.cacheHostname + ":" + Digiroad2Properties.cacheHostPort))

  def set[DataModel](key: String, ttl: Int, data: DataModel): DataModel = {
    try {
      LogUtils.time(logger,"Cache value")(
        client.set(key, ttl, data).isDone
      )
      data
    } catch {
      case e: Exception => logger.error("Caching failed", e); throw e
    }
  }

  def get[DataModel](key: String): CachedValue = {
    try {
      val result = LogUtils.time(logger,"Retrieve value from cache")(
        client.get(key).asInstanceOf[DataModel]
      )
      
      if (result == null) {
        CachedValue(null, success = false)
      } else {
        CachedValue(result, success = true)
      }
    } catch {
      case e: Exception => logger.error("Retrieval of cached value failed", e); throw e
    }
  }

}

object Caching extends CacheClient {
  def cache[DataModel](f: => DataModel)(key: String): DataModel = {
    if (Digiroad2Properties.caching) {
      get[DataModel](key) match {
        case CachedValue(data, true) =>
          logger.debug("Return cached value")
          data.asInstanceOf[DataModel]
        case _ =>
          logger.debug("Caching with key " + key)
          set[DataModel](key, defaultTTL, f)
      }
    } else {
      logger.debug("Caching turned off")
      f
    }
  }

  def flush(): Boolean = {
      val result = client.flush()
      result.get()
    }

}

