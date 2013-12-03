<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
  -->
Oak and OSGi
============

Discussion (3-Dec-2012)
-----------------------

### goals
* provide a pluggable and configurable way for oak
* leverage osgi
* allow simple ad-hoc configuration (todays: .with(...) pattern)

### current status
* don't reinvent the wheel
    * SecurityProvider / Whiteboard are used like a OSGi service registry 
    * security configuration classes are sometimes factories, sometimes pure configs

### suggestion
* osgi-container and the adhoc case should work the same
* for ad-hoc/standalone we start a felix container

### next steps
1. understand what is needed to start a mini-osgi (apache felix) container
2. try to change the oak-run (or similar) to create a oak-standalone that starts a osgi-container
3. configure "oak" using osgi semantics instead of a fixed 'with' list
4. use required configurations to turn on/off available plugins
5. use to 'with' pattern to automatically configure the osgi services
6. analyze what is needed to change the code in order to avoid passing the fake service registries and avoid static fields.

https://code.google.com/p/pojosr/wiki/Usage