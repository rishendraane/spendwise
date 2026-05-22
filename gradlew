#!/usr/bin/env sh
##############################################################################
# Gradle start up script for UN*X
#
# Copyright 2009-2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##############################################################################

progname="$0"

dirname="$(dirname "$progname")"
if [ "$dirname" = '.' ]; then
    dirname="$(pwd)"
fi

exec "${JAVA_HOME:-/usr}/bin/java" -classpath "$dirname/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
