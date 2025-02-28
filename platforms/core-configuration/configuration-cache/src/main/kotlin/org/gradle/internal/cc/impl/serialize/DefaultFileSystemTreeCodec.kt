/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.FilePrefixedTree
import org.gradle.internal.serialize.graph.FilePrefixedTree.Node
import org.gradle.internal.serialize.graph.FileSystemTreeDecoder
import org.gradle.internal.serialize.graph.FileSystemTreeEncoder
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

private const val EOF = -1


class DefaultFileSystemTreeEncoder(
    private val globalContext: CloseableWriteContext,
    private val prefixedTree: FilePrefixedTree
) : FileSystemTreeEncoder {

    override fun writeFile(writeContext: WriteContext, file: File) {
        val index = prefixedTree.insert(file)
        writeContext.writeSmallInt(index)
    }

    override suspend fun writeTree() {
        // no-op
    }

    override fun close() {
        globalContext.use {
            it.writePrefixedTreeNode(prefixedTree.compress(), null)
            it.writeSmallInt(EOF)
        }
    }

    private fun WriteContext.writePrefixedTreeNode(node: Node, parent: Node?) {
        writeSmallInt(node.index)
        writeBoolean(node.isFinal)
        writeString(node.segment)
        writeNullableSmallInt(parent?.index)
        node.children.entries.forEach { child ->
            writePrefixedTreeNode(child.value, node)
        }
    }
}

class DefaultFileSystemTreeDecoder(
    private val globalContext: CloseableReadContext,
) : FileSystemTreeDecoder {

    private
    class FutureFile {

        private
        val latch = CountDownLatch(1)

        private
        var file: File? = null

        fun complete(file: File) {
            this.file = file
            latch.countDown()
        }

        fun get(): File {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                throw TimeoutException("Timeout while waiting for file")
            }
            return file!!
        }
    }

    private val files = ConcurrentHashMap<Int, Any>()

    private
    val reader = thread(isDaemon = true) {
        val segments = HashMap<Int, String>()
        globalContext.use { context ->
            while (true) {
                val id = context.readSmallInt()
                if (id == EOF) break

                val isFinal = context.readBoolean()
                val segment = context.readString()
                val parent = context.readNullableSmallInt()

                val path = parent?.let { segments[it] + "/" + segment } ?: "/$segment"
                segments[id] = path

                if (isFinal) {
                    files.compute(id) { _, value ->
                        val file = File(path)
                        when (value) {
                            is FutureFile -> value.complete(file)
                            else -> require(value == null)
                        }
                        file
                    }
                }
            }
        }
    }

    override fun readFile(readContext: ReadContext): File =
        when (val file = files.computeIfAbsent(readContext.readSmallInt()) { FutureFile() }) {
            is FutureFile -> file.get()
            is File -> file
            else -> error("$file is unsupported")
        }

    override suspend fun readTree() {
        // no-op
    }

    override fun close() {
        reader.join(TimeUnit.MINUTES.toMillis(1))
    }
}
