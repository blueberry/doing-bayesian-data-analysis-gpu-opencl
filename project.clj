;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(defproject dbda "0.2.0-SNAPSHOT"
  :description "Doing Bayesian Data Analysis book on the GPU with Clojure, CUDA, and OpenCL"
  :author "Dragan Djuric"
  :url "http://github.com/dragandj/doing-bayesian-data-analysis-gpu"
  :scm {:name "git"
        :url "http://github.com/dragandj/doing-bayesian-data-analysis-gpu"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [uncomplicate/bayadera "0.2.0-SNAPSHOT"]]

  :profiles {:dev {:dependencies [[midje "1.9.1"]
                                  [org.clojure/data.csv "0.1.4"]]
                   :plugins [[lein-midje "3.2.1"]]
                   :global-vars {*warn-on-reflection* true
                                 *print-length* 16}
                   :jvm-opts ^:replace ["-Dclojure.compiler.direct-linking=true"
                                        "-XX:MaxDirectMemorySize=16g" "-XX:+UseLargePages"
                                        #_"--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"]}}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"])
