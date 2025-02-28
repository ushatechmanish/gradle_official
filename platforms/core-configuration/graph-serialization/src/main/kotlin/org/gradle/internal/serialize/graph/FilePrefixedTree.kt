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

package org.gradle.internal.serialize.graph

import org.gradle.internal.file.FilePathUtil
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * An implementation of space-optimized prefix tree for files.
 * Because of the space optimization focus, the effective lookup is possible only after building indexes table.
 * <p>
 * The tree can be compressed. The compressing factor is totally depends on the structure of the tree.
 */
@ServiceScope(BuildTree::class)
class FilePrefixedTree {

    private var currentIndex = AtomicInteger(0)

    val root = Node(null, "")


    /**
     * Inserts a file path into the tree, creating necessary nodes if they do not exist.
     * Insertion is thread-safe.
     *
     * @return the index of the final node representing the file.
     * If the path already exists, returns the existing index.
     * */
    fun insert(file: File): Int {
        val segments = FilePathUtil.getPathSegments(file.path)
        var current = root

        for (segment in segments) {
            if (segment.isEmpty()) {
                // leading separator
                continue
            }

            current = current.children.computeIfAbsent(segment) { Node(null, segment) }
        }

        if (current.isIntermediate) {
            current.index = currentIndex.getAndIncrement()
        }

        return current.index!!
    }

    /**
     * The only efficient way to perform a lookup in the tree.
     * <p>
     * Indexes are built by performing a depth-first search (DFS) through the entire tree,
     * creating a mapping between each index and its corresponding item.
     */
    fun buildIndexes(root: Node): Map<Int, File> {
        val indexes = mutableMapOf<Int, File>()
        buildIndexFor(root, mutableListOf(), indexes)
        return indexes
    }

    /**
     * Compresses the current tree following the <a href="http://en.wikipedia.org/wiki/Radix_tree">Radix tree</a> idea.
     * <p>
     * Tree should be compressed only after all insertions are completed.
     */
    fun compress(): Node = compressFrom(root)

    private fun compressFrom(node: Node): Node {
        if (node.isFinal) {
            return node.copy(children = node.children.compress())
        }

        val segmentsToCompress = mutableListOf<String>()
        var current = node

        while (current.children.size == 1 && current.singleChild.isIntermediate) {
            segmentsToCompress.add(current.segment)
            current = current.singleChild
        }

        if (current.isIntermediate) {
            segmentsToCompress.add(current.segment)
        }

        return when (current.children.size) {
            0 -> Node(current.index, segmentsToCompress.compress())
            // final child
            1 -> when (current.singleChild.children.size) {
                0 -> {
                    segmentsToCompress.add(current.singleChild.segment)
                    Node(current.singleChild.index, segmentsToCompress.compress())
                }

                else -> Node(current.index, segmentsToCompress.compress(), current.children.compress())
            }

            else -> Node(current.index, segmentsToCompress.compress(), current.children.compress())
        }
    }

    private fun List<String>.compress() = filter { it.isNotEmpty() }.joinToString("/")

    private fun Map<String, Node>.compress() = ConcurrentHashMap(values.map(::compressFrom).associateBy { it.segment })

    private fun buildIndexFor(node: Node, segments: MutableList<String>, indexes: MutableMap<Int, File>) {
        segments.add(node.segment)
        node.index?.let { idx ->
            indexes[idx] = File("/${segments.joinToString("/")}")
        }
        for (child in node.children.values) {
            buildIndexFor(child, segments, indexes)
        }
        segments.removeAt(segments.size - 1) // backtrack
    }

    data class Node(
        @Volatile var index: Int?,
        val segment: String,
        val children: MutableMap<String, Node> = ConcurrentHashMap()
    ) {
        val isIntermediate
            get() = index == null

        val isFinal
            get() = !isIntermediate

        val singleChild
            get() = children.entries.single().value

    }
}
