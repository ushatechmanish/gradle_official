/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;

/**
 * A factory for creating low level {@link ClientExecHandleBuilder} instances.
 *
 * This is very low level process API factory. It is not intended to be used directly except in very specific cases.
 * For starting a process prefer using ExecFactory.
 */
@NullMarked
@ServiceScope({Scope.Global.class})
public interface ClientExecHandleBuilderFactory {

    /**
     * Returns a new {@link ClientExecHandleBuilder} to build a new {@link ExecHandle}.
     */
    ClientExecHandleBuilder newExecHandleBuilder();
}
