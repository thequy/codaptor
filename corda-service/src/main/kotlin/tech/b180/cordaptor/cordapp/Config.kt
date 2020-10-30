package tech.b180.cordaptor.cordapp

import net.corda.core.cordapp.CordappConfig
import tech.b180.cordaptor.kernel.Config
import tech.b180.cordaptor.kernel.ConfigPath
import java.time.Duration

/**
 * Implements microkernel's [Config] interface by using CorDapp configuration available within Corda,
 * but referring to the fallback configuration to lookup missing values.
 */
class CordappConfigWithFallback(
    private val cordappConfig: CordappConfig,
    private val fallback: Config,
    private val pathPrefix: String = ""
) : Config {

  companion object {

    fun parseDuration(str: String): Duration {
      TODO()
    }

    fun parseBytesSize(str: String): Long {
      TODO()
    }
  }

  override fun pathExists(path: ConfigPath): Boolean {
    return if (cordappConfig.exists(pathPrefix + path)) {
      true
    } else {
      fallback.pathExists(path)
    }
  }

  override fun getSubtree(path: ConfigPath): Config {
    val prefixedPath = pathPrefix + path
    return if (cordappConfig.exists(prefixedPath)) {
      return CordappConfigWithFallback(cordappConfig, fallback.getSubtree(prefixedPath), "$prefixedPath.")
    } else {
      // nothing in the CorDapp config, so the subtree would be provided entirely by the fallback
      fallback.getSubtree(path)
    }
  }

  override fun getString(path: ConfigPath): String {
    return if (cordappConfig.exists(pathPrefix + path)) {
      return cordappConfig.getString(pathPrefix + path)
    } else {
      fallback.getString(path)
    }
  }

  override fun getDuration(path: ConfigPath): Duration {
    return if (cordappConfig.exists(pathPrefix + path)) {
      parseDuration(cordappConfig.getString(pathPrefix + path))
    } else {
      fallback.getDuration(path)
    }
  }

  override fun getInt(path: ConfigPath): Int {
    return if (cordappConfig.exists(pathPrefix + path)) {
      return cordappConfig.getInt(pathPrefix + path)
    } else {
      fallback.getInt(path)
    }
  }

  override fun getLong(path: ConfigPath): Long {
    return if (cordappConfig.exists(pathPrefix + path)) {
      return cordappConfig.getLong(pathPrefix + path)
    } else {
      fallback.getLong(path)
    }
  }

  override fun getDouble(path: ConfigPath): Double {
    return if (cordappConfig.exists(pathPrefix + path)) {
      return cordappConfig.getDouble(pathPrefix + path)
    } else {
      fallback.getDouble(path)
    }
  }

  override fun getBytesSize(path: ConfigPath): Long {
    return if (cordappConfig.exists(pathPrefix + path)) {
      return parseBytesSize(cordappConfig.getString(pathPrefix + path))
    } else {
      fallback.getBytesSize(path)
    }
  }

  override fun getBoolean(path: ConfigPath): Boolean {
    return if (cordappConfig.exists(pathPrefix + path)) {
      return cordappConfig.getBoolean(pathPrefix + path)
    } else {
      fallback.getBoolean(path)
    }
  }
}