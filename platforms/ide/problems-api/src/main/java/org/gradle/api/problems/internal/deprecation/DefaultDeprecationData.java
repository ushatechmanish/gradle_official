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

package org.gradle.api.problems.internal.deprecation;

import org.gradle.api.problems.deprecation.DeprecationData;
import org.gradle.api.problems.deprecation.ReportSource;

import javax.annotation.Nullable;
import java.io.Serializable;

public class DefaultDeprecationData implements DeprecationData, Serializable {

    private ReportSource source;
    private String removedIn;
    private String replacedBy;
    private String because;

    @Nullable
    @Override
    public ReportSource getSource() {
        return source;
    }

    public void setSource(ReportSource source) {
        this.source = source;
    }

    @Nullable
    @Override
    public String getRemovedIn() {
        return removedIn;
    }

    public void setRemovedIn(String removedIn) {
        this.removedIn = removedIn;
    }

    @Nullable
    @Override
    public String getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(String replacedBy) {
        this.replacedBy = replacedBy;
    }

    @Nullable
    @Override
    public String getBecause() {
        return because;
    }

    public void setBecause(String because) {
        this.because = because;
    }
}
