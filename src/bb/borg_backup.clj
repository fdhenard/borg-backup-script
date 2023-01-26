(ns borg-backup
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [babashka.process :as p :refer [sh]]))


(defn config []
  (let [path (System/getenv "BORG_BB_CONFIG_PATH")]
    (-> path slurp edn/read-string)))

;; 1/26/23 - clojure.core.memoize is not yet available for bb
;; (def config
;;   (memo/ttl config* :ttl/threshold 30)) ;; 30 seconds

(comment

  (config)

  (p/process "echo hello")

  (sh "echo hello")

  (sh "echo" "hello")
  )

(defn my-sh [& args]
  (let [{:keys [exit] :as res} (apply sh (remove nil? args))]
    (if (= 0 exit)
      res
      (throw (ex-info "error" res)))))


(defn prune! [is-prod?]
  (let [_ (println "running prune")]
    (my-sh "borg" "prune"
           "-v"
           "--list"
           (when-not is-prod?
             "--dry-run")
           "--keep-daily" "7"
           "--keep-weekly" "4"
           (:repo-path (config)))))

(comment

  (prune! false)

  (try
    (prune! false)
    (catch Exception e
      (ex-data e)))

  )

(def chicago-tz (java.time.ZoneId/of "America/Chicago"))

(defn time->file-nameable-str [zoned-datetime]
  (let [file-nameable-fmt
        (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH-mm-ss")]
   (.format zoned-datetime file-nameable-fmt)))

(defn bkp-name->target [bkp-name]
  (str (:repo-path (config)) "::" bkp-name))

(defn backup! [is-prod?]
  (let [_ (println "running backup")
        curr-run-time
        (-> (java.time.ZonedDateTime/now)
            (.withZoneSameInstant chicago-tz))
        bkp-tgt (bkp-name->target (time->file-nameable-str curr-run-time))
        _ (println (with-out-str (pp/pprint {:is-prod? is-prod?
                                             :bkp-tgt bkp-tgt})))]
    (my-sh "borg" "create"
           "--patterns-from" (:patterns-path (config))
           (when-not is-prod?
             "--dry-run")
           "--list"
           bkp-tgt
           (:dir-to-backup (config)))))

(comment

  (backup! false)

  )

(defn prune-and-backup! [is-prod?]
  (let [prune-res (prune! is-prod?)
        _ (println "prune out:" (:out prune-res))
        bkp-res (backup! is-prod?)
        _ (println "bkp out:" (:out bkp-res))
        _ (println "bkp err:" (:err bkp-res))]))

(comment

  (prune-and-backup! false)

  )

(defn prune-and-backup-w-args! [args]
  (println "args" (prn args))
  (let [is-prod? (contains? (set args) "prod")
        _ (println "is prod = " is-prod?)]
    (prune-and-backup! is-prod?)))

(defn do-to-repo! [arg]
  (if (= "delete" arg)
    (throw (ex-info "no delete please" {}))
    (let [do-res (my-sh "borg" arg (:repo-path (config)))
          _ (println (str "out:\n" (:out do-res)))
          _ (println (str "err:\n" (:err do-res)))])))

(comment

  (disj #{"what"} "what")

  )

(defn delete-bkp! [args]
  (let [args-set (set args)
        is-prod? (contains? args-set "--prod")
        remaining (disj args-set "--prod")
        _ (when-not (= 1 (count remaining))
            (throw (ex-info "expecting exactly one backup name" {:args args})))
        bkp-name (first remaining)
        bkp-target (bkp-name->target bkp-name)
        _ (println "deleting")
        _ (println (with-out-str (pp/pprint {:is-prod? is-prod?
                                             :bkp-target bkp-target})))
        del-res (my-sh "borg" "delete"
                       (when-not is-prod?
                         "--dry-run")
                       bkp-target)
        _ (println (str "out:\n" (:out del-res)))
        _ (println (str "err:\n" (:err del-res)))]))
