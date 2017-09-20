(def ps-version "5.1.1-SNAPSHOT")
(def jruby-1_7-version "1.7.27-1")
(def jruby-9k-version "9.1.11.0-1")

(defn deploy-info
  [url]
  { :url url
    :username :env/nexus_jenkins_username
    :password :env/nexus_jenkins_password
    :sign-releases false })

(def heap-size-from-profile-clj
  (let [profile-clj (io/file (System/getenv "HOME") ".lein" "profiles.clj")]
    (if (.exists profile-clj)
      (-> profile-clj
        slurp
        read-string
        (get-in [:user :puppetserver-heap-size])))))

(defn heap-size
  [default-heap-size heap-size-type]
  (or
    (System/getenv "PUPPETSERVER_HEAP_SIZE")
    heap-size-from-profile-clj
    (do
      (println "Using" default-heap-size heap-size-type
        "heap since not set via PUPPETSERVER_HEAP_SIZE environment variable or"
        "user.puppetserver-heap-size in ~/.lein/profiles.clj file. Set to at"
        "least 5G for best performance during test runs.")
      default-heap-size)))

(def figwheel-version "0.3.7")
(def cljsbuild-version "1.1.5")

(defproject puppetlabs/puppetserver ps-version
  :description "Puppet Server"

  :min-lein-version "2.7.1"

  :parent-project {:coords [puppetlabs/clj-parent "1.4.1"]
                   :inherit [:managed-dependencies]}

  :dependencies [[org.clojure/clojure]

                 [slingshot]
                 [circleci/clj-yaml]
                 [org.yaml/snakeyaml]
                 [commons-lang]
                 [commons-io]

                 [clj-time]
                 [prismatic/schema]
                 [me.raynes/fs]
                 [liberator]
                 [org.apache.commons/commons-exec]
                 [io.dropwizard.metrics/metrics-core]

                 ;; We do not currently use this dependency directly, but
                 ;; we have documentation that shows how users can use it to
                 ;; send their logs to logstash, so we include it in the jar.
                 ;; we may use it directly in the future
                 ;; We are using an exlusion here because logback dependencies should
                 ;; be inherited from trapperkeeper to avoid accidentally bringing
                 ;; in different versions of the three different logback artifacts
                 [net.logstash.logback/logstash-logback-encoder]

                 [puppetlabs/jruby-utils "0.10.0"]
                 [puppetlabs/jruby-deps ~jruby-1_7-version]

                 ;; JRuby 1.7.x and trapperkeeper (via core.async) both bring in
                 ;; asm dependencies.  Deferring to clj-parent to resolve the version.
                 [org.ow2.asm/asm-all]

                 [puppetlabs/trapperkeeper]
                 [puppetlabs/trapperkeeper-authorization]
                 [puppetlabs/trapperkeeper-comidi-metrics]
                 [puppetlabs/trapperkeeper-metrics]
                 [puppetlabs/trapperkeeper-scheduler]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/kitchensink]
                 [puppetlabs/ssl-utils]
                 [puppetlabs/ring-middleware]
                 [puppetlabs/dujour-version-check]
                 [puppetlabs/http-client]
                 [puppetlabs/comidi]
                 [puppetlabs/i18n]

                 ;; dependencies for clojurescript dashboard
                 [puppetlabs/cljs-dashboard-widgets]
                 [org.clojure/clojurescript]
                 [cljs-http "0.1.36"]]

  :main puppetlabs.trapperkeeper.main

  :pedantic? :abort

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  :test-paths ["test/unit" "test/integration"]
  :resource-paths ["resources" "src/ruby" "target/js-resources"]

  :repositories [["releases" "http://nexus.delivery.puppetlabs.net/content/repositories/releases/"]
                 ["snapshots" "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/"]]

  :plugins [[lein-parent "0.3.1"]
            [puppetlabs/i18n "0.8.0"]]

  :uberjar-name "puppet-server-release.jar"
  :lein-ezbake {:vars {:user "puppet"
                       :group "puppet"
                       :build-type "foss"
                       :java-args ~(str "-Xms2g -Xmx2g "
                                     "-Djruby.logger.class=com.puppetlabs.jruby_utils.jruby.Slf4jLogger")
                       :create-dirs ["/opt/puppetlabs/server/data/puppetserver/jars"]
                       :repo-target "PC1"
                       :bootstrap-source :services-d
                       :logrotate-enabled false
                       :redhat-postinst-triggers [ { :package "puppet-agent", :script ["echo 'hi'", "true"] }, { :package "vim", :script ["true"]} ]
                       }
                :resources {:dir "tmp/ezbake-resources"}
                :config-dir "ezbake/config"
                :system-config-dir "ezbake/system-config"
                :additional-uberjars [[puppetlabs/jruby-deps ~jruby-9k-version]
                                      [puppetlabs/jruby-deps ~jruby-1_7-version]]}

  :deploy-repositories [["releases" ~(deploy-info "http://nexus.delivery.puppetlabs.net/content/repositories/releases/")]
                        ["snapshots" ~(deploy-info "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/")]]

  ;; By declaring a classifier here and a corresponding profile below we'll get an additional jar
  ;; during `lein jar` that has all the code in the test/ directory. Downstream projects can then
  ;; depend on this test jar using a :classifier in their :dependencies to reuse the test utility
  ;; code that we have.
  :classifiers [["test" :testutils]]

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "target/js-resources/puppetlabs/puppetserver/public/js/puppetserver-dashboard.js"
                                        :output-dir    "target/js-resources/puppetlabs/puppetserver/public/js/out"
                                        :asset-path   "js/out"
                                        :optimizations :none
                                        :pretty-print  true
                                        :main "puppetlabs.puppetserver.dashboard.production"}}}}
  :hooks [leiningen.cljsbuild]

  :profiles {:dev {:source-paths  ["dev"]
                   :dependencies  [[org.clojure/tools.namespace]
                                   [puppetlabs/trapperkeeper-webserver-jetty9 nil]
                                   [puppetlabs/trapperkeeper-webserver-jetty9 nil :classifier "test"]
                                   [puppetlabs/trapperkeeper nil :classifier "test" :scope "test"]
                                   [puppetlabs/trapperkeeper-metrics :classifier "test" :scope "test"]
                                   [puppetlabs/kitchensink nil :classifier "test" :scope "test"]
                                   [ring-basic-authentication]
                                   [ring-mock]
                                   [grimradical/clj-semver "0.3.0" :exclusions [org.clojure/clojure]]
                                   [beckon]
                                   [com.cemerick/url "0.1.1"]

                                   ;; Including this to avoid a logback version conflict when running
                                   ;; 'lein figwheel'.
                                   [ch.qos.logback/logback-classic]

                                   ;; dependencies for cljs development
                                   [leiningen "2.7.1" :exclusions [org.codehaus.plexus/plexus-utils
                                                                   org.clojure/tools.cli]]
                                   [cljsbuild ~cljsbuild-version]

                                   [figwheel ~figwheel-version :exclusions [org.clojure/clojure]]]
                   ;; dev profile config for clojurescript dev
                   :plugins [[lein-cljsbuild ~cljsbuild-version]
                             [lein-figwheel ~figwheel-version
                              :exclusions [org.clojure/clojure
                                           org.clojure/core.cache
                                           commons-io
                                           commons-codec]]]
                   :figwheel {:http-server-root "puppetlabs/puppetserver/public"
                              :server-port 3449
                              :repl false}
                   :cljsbuild {:builds {:app {:source-paths ["dev-cljs"]
                                              :compiler {:main "puppetlabs.puppetserver.dashboard.dev"
                                                         :source-map true}}}}
                   ; SERVER-332, enable SSLv3 for unit tests that exercise SSLv3
                   :jvm-opts      ["-Djava.security.properties=./dev-resources/java.security"]}

             :testutils {:source-paths ^:replace ["test/unit" "test/integration"]}
             :test {
                    ;; NOTE: In core.async version 0.2.382, the default size for
                    ;; the core.async dispatch thread pool was reduced from
                    ;; (42 + (2 * num-cpus)) to... eight.  The jruby metrics tests
                    ;; use core.async and need more than eight threads to run
                    ;; properly; this setting overrides the default value.  Without
                    ;; it the metrics tests will hang.
                    :jvm-opts ["-Dclojure.core.async.pool-size=50"]
                    }

             :ezbake {:dependencies ^:replace [;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                                               ;; NOTE: we need to explicitly pass in `nil` values
                                               ;; for the version numbers here in order to correctly
                                               ;; inherit the versions from our parent project.
                                               ;; This is because of a bug in lein 2.7.1 that
                                               ;; prevents the deps from being processed properly
                                               ;; with `:managed-dependencies` when you specify
                                               ;; dependencies in a profile.  See:
                                               ;; https://github.com/technomancy/leiningen/issues/2216
                                               ;; Hopefully we can remove those `nil`s (if we care)
                                               ;; and this comment when lein 2.7.2 is available.
                                               ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

                                               ;; we need to explicitly pull in our parent project's
                                               ;; clojure version here, because without it, lein
                                               ;; brings in its own version, and older versions of
                                               ;; lein depend on clojure 1.6.
                                               [org.clojure/clojure nil]
                                               [puppetlabs/puppetserver ~ps-version :exclusions [puppetlabs/jruby-deps]]
                                               [puppetlabs/trapperkeeper-webserver-jetty9 nil]
                                               [org.clojure/tools.nrepl nil]]
                      :plugins [[puppetlabs/lein-ezbake "1.6.2-SNAPSHOT"]]
                      :name "puppetserver"}
             :uberjar {:aot [puppetlabs.trapperkeeper.main]
                       :dependencies [[puppetlabs/trapperkeeper-webserver-jetty9 nil]]}
             :ci {:plugins [[lein-pprint "1.1.1"]]}
             :voom {:plugins [[lein-voom "0.1.0-20150115_230705-gd96d771" :exclusions [org.clojure/clojure]]]}
             :jruby9k {:dependencies [[puppetlabs/jruby-deps ~jruby-9k-version]]}}

  :test-selectors {:integration :integration
                   :unit (complement :integration)}

  :aliases {"gem" ["trampoline" "run" "-m" "puppetlabs.puppetserver.cli.gem" "--config" "./dev/puppetserver.conf" "--"]
            "ruby" ["trampoline" "run" "-m" "puppetlabs.puppetserver.cli.ruby" "--config" "./dev/puppetserver.conf" "--"]
            "irb" ["trampoline" "run" "-m" "puppetlabs.puppetserver.cli.irb" "--config" "./dev/puppetserver.conf" "--"]}

  ; tests use a lot of PermGen (jruby instances)
  :jvm-opts ["-Djruby.logger.class=com.puppetlabs.jruby_utils.jruby.Slf4jLogger"
             "-XX:+UseG1GC"
             ~(str "-Xms" (heap-size "1G" "min"))
             ~(str "-Xmx" (heap-size "2G" "max"))]

  :repl-options {:init-ns dev-tools}

  ;; This is used to merge the locales.clj of all the dependencies into a single
  ;; file inside the uberjar
  :uberjar-merge-with {"locales.clj"  [(comp read-string slurp)
                                       (fn [new prev]
                                         (if (map? prev) [new prev] (conj prev new)))
                                       #(spit %1 (pr-str %2))]}
  )
