package com.ollitert.llm.server.service

import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * An [InputStream] backed by a [LinkedBlockingQueue] of byte-array chunks.
 *
 * A writer thread calls [enqueue] to push data and [finish] to signal EOF.
 * The reader thread (NanoHTTPD) calls [read] which blocks until data arrives
 * or EOF is signalled.
 *
 * Unlike [java.io.PipedInputStream], this has no thread-affinity requirements
 * and produces a clean EOF (-1) that NanoHTTPD can use to properly terminate
 * chunked transfer encoding.
 *
 * The [cancel] method can be called from the reader thread when the client
 * disconnects, signalling the writer (inference) to stop.
 */
class BlockingQueueInputStream : InputStream() {

  private val queue = LinkedBlockingQueue<ByteArray>()

  /** Sentinel object — identity-compared, never actually read. */
  private val eof = ByteArray(0)

  private var current: ByteArray? = null
  private var pos = 0
  private var done = false

  /** Set when the client disconnects. Checked by the inference thread. */
  @Volatile
  var isCancelled = false
    private set

  /** Enqueue UTF-8 text to be read by the consumer. */
  fun enqueue(text: String) {
    if (isCancelled) return
    queue.put(text.toByteArray(Charsets.UTF_8))
  }

  /** Signal that no more data will be written. */
  fun finish() {
    queue.put(eof)
  }

  /** Signal cancellation (client disconnect). Unblocks any pending [read]. */
  fun cancel() {
    isCancelled = true
    queue.put(eof) // unblock the reader if it's waiting
  }

  override fun read(): Int {
    if (done) return -1
    val buf = currentOrNext() ?: return -1
    val b = buf[pos++].toInt() and 0xFF
    if (pos >= buf.size) { current = null; pos = 0 }
    return b
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (done) return -1
    val buf = currentOrNext() ?: return -1
    val available = buf.size - pos
    val n = minOf(available, len)
    System.arraycopy(buf, pos, b, off, n)
    pos += n
    if (pos >= buf.size) { current = null; pos = 0 }
    return n
  }

  /** Returns the current chunk, or takes the next from the queue. Returns null on EOF. */
  private fun currentOrNext(): ByteArray? {
    current?.let { return it }
    val next = queue.take()
    if (next === eof) { done = true; return null }
    current = next
    pos = 0
    return next
  }
}
