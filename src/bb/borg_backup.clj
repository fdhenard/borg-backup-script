(ns borg-backup
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [babashka.fs :as fs]
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
  (let [[opts args] (if (map? (first args))
                      [(first args) (rest args)]
                      [{} args])
        {:keys [exit] :as res} (apply sh (merge {:out :inherit :err :inherit} opts)
                                     (remove nil? args))]
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
        _ (pp/pprint {:is-prod? is-prod?
                      :bkp-tgt bkp-tgt})
        sh-args ["borg" "create"
                 "--patterns-from" (:patterns-path (config))
                 (when-not is-prod?
                   "--dry-run")
                 "--list"
                 bkp-tgt
                 (:dir-to-backup (config))]]
    (apply my-sh sh-args)))

(comment

  (backup! false)

  )

(defn prune-and-backup! [is-prod?]
  (backup! is-prod?)
  (prune! is-prod?)
  (if is-prod?
    (do
      (println "running compact")
      (my-sh "borg" "compact" (:repo-path (config))))
    (println "skipping compact in dry run")))

(comment

  (prune-and-backup! false)

  )

(defn prune-and-backup-w-args! [args]
  (println "args" (prn args))
  (let [is-prod? (contains? (set args) "prod")
        _ (println "is prod = " is-prod?)]
    (prune-and-backup! is-prod?)))

(defn archive-target [repo-path backup-name]
  (when-not (seq backup-name)
    (throw (ex-info "expected a backup name or latest-backup" {})))
  (let [archive (if (= "latest-backup" backup-name)
                  (str/trim
                   (:out (my-sh {:out :string} "borg" "list" "--short" "--sort-by" "timestamp"
                                "--last" "1" repo-path)))
                  backup-name)]
    (when-not (seq archive)
      (throw (ex-info "repository has no backups" {:repo-path repo-path})))
    (str repo-path "::" archive)))

(defn list-files! [backup-name]
  (my-sh "borg" "list" (archive-target (:repo-path (config)) backup-name)))

(defn restore-destination! [path]
  (let [destination (fs/absolutize path)]
    (when-not (or (fs/exists? destination) (fs/sym-link? destination))
      (fs/create-dirs destination))
    (when (or (fs/sym-link? destination)
              (not (fs/directory? destination))
              (seq (fs/list-dir destination)))
      (throw (ex-info "Restore destination must be an empty directory"
                      {:destination (str destination)})))
    (str destination)))

(defn restore! [& [option path backup-name & paths]]
  (let [usage "Usage: bb restore --to <directory> [backup-name|latest-backup [archive-path ...]]"]
    (if (= "--help" option)
      (println usage)
      (do
        (when (or (not= "--to" option) (str/blank? path) (str/starts-with? path "--"))
          (throw (ex-info usage {})))
        (let [repo-path (:repo-path (config))
              ;; Borg treats a colon before the first slash as a remote host or URL.
              repo-path (if (re-find #"^[^/]+:" repo-path)
                          repo-path
                          (str (fs/absolutize repo-path)))
              target (archive-target repo-path (or backup-name "latest-backup"))
              destination (restore-destination! path)]
          (println "Extracting to" destination)
          (flush)
          (try
            (apply my-sh {:dir destination :in :inherit} "borg" "extract" "--progress" "--"
                   target paths)
            (catch Exception e
              (throw (ex-info (str "Restore did not finish successfully. Check Borg's output. "
                                   "Any extracted files are in " destination)
                              {:destination destination} e))))
          (println "Restore complete. Files are in" destination))))))

(defn do-to-repo! [arg & args]
  (when (= "delete" arg)
    (throw (ex-info "no delete please" {})))
  (when-not (= (count args) (if (= "list-files" arg) 1 0))
    (throw (ex-info "expected: repo-do <command>, or repo-do list-files <backup-name|latest-backup>"
                    {:command arg :args args})))
  (if (= "list-files" arg)
    (list-files! (first args))
    (my-sh "borg" arg (:repo-path (config)))))

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
                       bkp-target)]
    del-res))
